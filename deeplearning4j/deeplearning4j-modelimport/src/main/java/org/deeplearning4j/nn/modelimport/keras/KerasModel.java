/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  *  See the NOTICE file distributed with this work for additional
 *  *  information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */

package org.deeplearning4j.nn.modelimport.keras;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.graph.PreprocessorVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.Convolution3D;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.samediff.SameDiffLambdaLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.config.KerasLayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.KerasModelConfiguration;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.layers.KerasInput;
import org.deeplearning4j.nn.modelimport.keras.layers.KerasLoss;
import org.deeplearning4j.nn.modelimport.keras.layers.core.KerasLambda;
import org.deeplearning4j.nn.modelimport.keras.layers.recurrent.KerasLSTM;
import org.deeplearning4j.nn.modelimport.keras.layers.recurrent.KerasRnnUtils;
import org.deeplearning4j.nn.modelimport.keras.layers.recurrent.KerasSimpleRnn;
import org.deeplearning4j.nn.modelimport.keras.utils.KerasLayerUtils;
import org.deeplearning4j.nn.modelimport.keras.utils.KerasModelBuilder;
import org.deeplearning4j.nn.modelimport.keras.utils.KerasModelUtils;
import org.deeplearning4j.nn.modelimport.keras.utils.KerasOptimizerUtils;
import org.deeplearning4j.util.Convolution3DUtils;
import org.deeplearning4j.util.ConvolutionUtils;
import org.nd4j.autodiff.samediff.internal.DependencyList;
import org.nd4j.autodiff.samediff.internal.DependencyTracker;
import org.nd4j.common.primitives.Counter;
import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.learning.config.IUpdater;
import org.nd4j.shade.guava.collect.Lists;
import org.tensorflow.framework.NodeDef;

import java.io.IOException;
import java.util.*;

import static org.deeplearning4j.nn.modelimport.keras.KerasLayer.customLayers;
import static org.deeplearning4j.nn.modelimport.keras.KerasLayer.lambdaLayers;

@Slf4j
@Data
public class KerasModel {

    protected static KerasModelConfiguration config = new KerasModelConfiguration();
    protected KerasModelBuilder modelBuilder = new KerasModelBuilder(config);

    protected String className; // Keras model class name
    protected boolean enforceTrainingConfig; // whether to build model in training mode
    protected Map<String, KerasLayer> layers; // map from layer name to KerasLayer
    protected List<KerasLayer> layersOrdered; // ordered list of layers
    protected Map<String, InputType> outputTypes; // inferred output types for all layers
    protected ArrayList<String> inputLayerNames; // list of input layers
    protected ArrayList<String> outputLayerNames; // list of output layers
    protected boolean useTruncatedBPTT = false; // whether to use truncated BPTT
    protected int truncatedBPTT = 0; // truncated BPTT value
    protected int kerasMajorVersion;
    protected String kerasBackend;
    protected KerasLayer.DimOrder dimOrder = null;
    protected IUpdater optimizer = null;

    public KerasModel() {
    }

    public KerasModelBuilder modelBuilder() {
        return this.modelBuilder;
    }

    /**
     * (Recommended) Builder-pattern constructor for (Functional API) Model.
     *
     * @param modelBuilder builder object
     * @throws IOException                            IO exception
     * @throws InvalidKerasConfigurationException     Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public KerasModel(KerasModelBuilder modelBuilder)
            throws UnsupportedKerasConfigurationException, IOException, InvalidKerasConfigurationException {
        this(modelBuilder.getModelJson(), modelBuilder.getModelYaml(), modelBuilder.getWeightsArchive(),
                modelBuilder.getWeightsRoot(), modelBuilder.getTrainingJson(), modelBuilder.getTrainingArchive(),
                modelBuilder.isEnforceTrainingConfig(), modelBuilder.getInputShape(), modelBuilder.getDimOrder());
    }

    /**
     * (Not recommended) Constructor for (Functional API) Model from model configuration
     * (JSON or YAML), training configuration (JSON), weights, and "training mode"
     * boolean indicator. When built in training mode, certain unsupported configurations
     * (e.g., unknown regularizers) will throw Exceptions. When enforceTrainingConfig=false, these
     * will generate warnings but will be otherwise ignored.
     *
     * @param modelJson             model configuration JSON string
     * @param modelYaml             model configuration YAML string
     * @param enforceTrainingConfig whether to enforce training-related configurations
     * @throws IOException                            IO exception
     * @throws InvalidKerasConfigurationException     Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    protected KerasModel(String modelJson, String modelYaml, Hdf5Archive weightsArchive, String weightsRoot,
                         String trainingJson, Hdf5Archive trainingArchive, boolean enforceTrainingConfig,
                         int[] inputShape, KerasLayer.DimOrder dimOrder)
            throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {

        Map<String, Object> modelConfig = KerasModelUtils.parseModelConfig(modelJson, modelYaml);
        this.kerasMajorVersion = KerasModelUtils.determineKerasMajorVersion(modelConfig, config);
        this.kerasBackend = KerasModelUtils.determineKerasBackend(modelConfig, config);
        this.enforceTrainingConfig = enforceTrainingConfig;
        this.dimOrder = dimOrder;

        /* Determine model configuration type. */
        if (!modelConfig.containsKey(config.getFieldClassName()))
            throw new InvalidKerasConfigurationException(
                    "Could not determine Keras model class (no " + config.getFieldClassName() + " field found)");
        this.className = (String) modelConfig.get(config.getFieldClassName());
        if (!this.className.equals(config.getFieldClassNameModel()) && !this.className.equals(config.getFieldNameClassFunctional()))
            throw new InvalidKerasConfigurationException(
                    "Expected model class name " + config.getFieldClassNameModel() + " or " + config.getFieldNameClassFunctional() + " (found " + this.className + ")");


        /* Retrieve lists of input and output layers, layer configurations. */
        if (!modelConfig.containsKey(config.getModelFieldConfig()))
            throw new InvalidKerasConfigurationException("Could not find model configuration details (no "
                    + config.getModelFieldConfig() + " in model config)");
        Map<String, Object> layerLists = (Map<String, Object>) modelConfig.get(config.getModelFieldConfig());


        /* Construct list of input layers. */
        if (!layerLists.containsKey(config.getModelFieldInputLayers()))
            throw new InvalidKerasConfigurationException("Could not find list of input layers (no "
                    + config.getModelFieldInputLayers() + " field found)");
        this.inputLayerNames = new ArrayList<>();
        for (Object inputLayerNameObj : (List<Object>) layerLists.get(config.getModelFieldInputLayers()))
            this.inputLayerNames.add((String) ((List<Object>) inputLayerNameObj).get(0));

        /* Construct list of output layers. */
        if (!layerLists.containsKey(config.getModelFieldOutputLayers()))
            throw new InvalidKerasConfigurationException("Could not find list of output layers (no "
                    + config.getModelFieldOutputLayers() + " field found)");
        this.outputLayerNames = new ArrayList<>();
        for (Object outputLayerNameObj : (List<Object>) layerLists.get(config.getModelFieldOutputLayers()))
            this.outputLayerNames.add((String) ((List<Object>) outputLayerNameObj).get(0));

        /* Process layer configurations. */
        if (!layerLists.containsKey(config.getModelFieldLayers()))
            throw new InvalidKerasConfigurationException(
                    "Could not find layer configurations (no " + (config.getModelFieldLayers() + " field found)"));
        Pair<Map<String, KerasLayer>, List<KerasLayer>> layerPair =
                prepareLayers((List<Object>) layerLists.get((config.getModelFieldLayers())));
        this.layers = layerPair.getFirst();
        this.layersOrdered = layerPair.getSecond();

        /* Import training configuration. */
        if (enforceTrainingConfig) {
            if (trainingJson != null)
                importTrainingConfiguration(trainingJson);
            else log.warn("If enforceTrainingConfig is true, a training " +
                    "configuration object has to be provided. Usually the only practical way to do this is to store" +
                    " your keras model with `model.save('model_path.h5')`. If you store model config and weights" +
                    " separately no training configuration is attached.");
        }

        if(inputShape == null) {
            inputShape = layersOrdered.get(0).inputShape;
        }

        /* Infer output types for each layer. */
        this.outputTypes = inferOutputTypes(inputShape);

        /* Store weights in layers. */
        if (weightsArchive != null)
            KerasModelUtils.importWeights(weightsArchive, weightsRoot, layers, kerasMajorVersion, kerasBackend);
    }

    /**
     * Helper method called from constructor. Converts layer configuration
     * JSON into KerasLayer objects.
     *
     * @param layerConfigs List of Keras layer configurations
     */
    Pair<Map<String, KerasLayer>, List<KerasLayer>> prepareLayers(List<Object> layerConfigs)
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        Map<String, KerasLayer> layers = new HashMap<>(); // map from layer name to KerasLayer
        List<KerasLayer> layersOrdered = new ArrayList<>();

        for (Object layerConfig : layerConfigs) {
            Map<String, Object> layerConfigMap = (Map<String, Object>) layerConfig;
            // Append major keras version and backend to each layer config.
            layerConfigMap.put(config.getFieldKerasVersion(), this.kerasMajorVersion);
            if (kerasMajorVersion == 2 && this.kerasBackend != null)
                layerConfigMap.put(config.getFieldBackend(), this.kerasBackend);

            KerasLayerConfiguration kerasLayerConf = new KerasLayer(this.kerasMajorVersion).conf;

            if (dimOrder != null) { // Force override of dim ordering with value from model builder
                String dimOrderString;
                if (dimOrder == KerasLayer.DimOrder.TENSORFLOW)
                    dimOrderString = kerasLayerConf.getDIM_ORDERING_TENSORFLOW();
                else if (dimOrder == KerasLayer.DimOrder.THEANO)
                    dimOrderString = kerasLayerConf.getDIM_ORDERING_THEANO();
                else
                    throw new InvalidKerasConfigurationException("Invalid data format / dim ordering");
                layerConfigMap.put(kerasLayerConf.getLAYER_FIELD_DIM_ORDERING(), dimOrderString);
            }


            KerasLayer layer = KerasLayerUtils.getKerasLayerFromConfig(
                    layerConfigMap, this.enforceTrainingConfig, kerasLayerConf, customLayers, lambdaLayers, layers);
            layersOrdered.add(layer);
            layers.put(layer.getLayerName(), layer);
            if (layer instanceof KerasLSTM)
                this.useTruncatedBPTT = this.useTruncatedBPTT || ((KerasLSTM) layer).getUnroll();
            if (layer instanceof KerasSimpleRnn)
                this.useTruncatedBPTT = this.useTruncatedBPTT || ((KerasSimpleRnn) layer).getUnroll();
        }

        List<String> names = new ArrayList<>();
        //set of names of lambda nodes
        Set<String> lambdaNames = new HashSet<>();

        //node inputs by name for looking up which nodes to do replacements for (useful since indices of nodes can change)
        Map<String,List<String>> nodesOutputToForLambdas = new HashMap<>();
        for(int i = 0; i < layers.size(); i++) {
            names.add(layersOrdered.get(i).getLayerName());
            if(layersOrdered.get(i) instanceof KerasLambda) {
                lambdaNames.add(layersOrdered.get(i).getLayerName());
            }
        }

        Map<String,List<String>> replacementNamesForLambda = new HashMap<>();
        Map<Integer,KerasLayer> updatedOrders = new HashMap<>();
        for(int i = 0; i < layersOrdered.size(); i++) {
            KerasLayer kerasLayer = layers.get(names.get(i));
            List<String> tempCopyNames = new ArrayList<>(kerasLayer.getInboundLayerNames());
            List<String> removed = new ArrayList<>();

            for(String input : tempCopyNames) {
                //found a lambda where an input occurs, record the index for input
                if(lambdaNames.contains(input)) {
                    if(!nodesOutputToForLambdas.containsKey(input)) {
                        nodesOutputToForLambdas.put(input,new ArrayList<String>());
                    }

                    nodesOutputToForLambdas.get(input).add(kerasLayer.getLayerName());
                }
                //potential loop found
                int indexOfInput = names.indexOf(input);
                if(indexOfInput > i) {
                    KerasLambda originalLambda = (KerasLambda) kerasLayer;
                    Map<String,Object> configCopy = new HashMap<String,Object>(kerasLayer.originalLayerConfig);
                    String newName = kerasLayer.getLayerName() + "-" + input;
                    if(!replacementNamesForLambda.containsKey(originalLambda.layerName)) {
                        replacementNamesForLambda.put(originalLambda.layerName,new ArrayList<String>());
                    }
                    configCopy.put(kerasLayer.conf.getLAYER_FIELD_NAME(),newName);
                    replacementNamesForLambda.get(originalLambda.layerName).add(newName);
                    SameDiffLambdaLayer sameDiffLambdaLayer = (SameDiffLambdaLayer) originalLambda.getSameDiffLayer().clone();
                    sameDiffLambdaLayer.setLayerName(newName);
                    KerasLambda kerasLambda = new KerasLambda(configCopy,sameDiffLambdaLayer);
                    kerasLambda.layerName = newName;
                    kerasLambda.setInboundLayerNames(new ArrayList<>(Arrays.asList(input)));
                    layers.put(newName,kerasLambda);
                    int indexOfNewLayer = names.indexOf(input) + 1;
                    updatedOrders.put(indexOfNewLayer,kerasLambda);
                    names.add(indexOfNewLayer,newName);
                    removed.add(input);
                    System.out.println("Found input " + input + " at keras node " + names.get(i) + " with potential cycle.");

                }
            }

            kerasLayer.getInboundLayerNames().removeAll(removed);
        }




        //update the list with all the new layers
        for(Map.Entry<Integer,KerasLayer> newLayers : updatedOrders.entrySet()) {
            layersOrdered.add(newLayers.getKey(),newLayers.getValue());
        }

        List<String> oldNames = new ArrayList<>(names);

        names.clear();
        //old names are used for checking distance from old nodes to new ones
        //node inputs by name for looking up which nodes to do replacements for (useful since indices of nodes can change)
        if(!replacementNamesForLambda.isEmpty()) {
            for (Map.Entry<String, List<String>> replacementEntry : replacementNamesForLambda.entrySet()) {
                List<String> nodesToReplaceInputNamesWith = nodesOutputToForLambdas.get(replacementEntry.getKey());
                Set<String> processed = new HashSet<>();
                for (String nodeName : nodesToReplaceInputNamesWith) {
                    KerasLayer kerasLayer = layers.get(nodeName);
                    boolean shouldBeOriginal = true;
                    if (!processed.isEmpty()) {
                        for (String process : processed) {
                            if (kerasLayer.getInboundLayerNames().contains(process)) {
                                shouldBeOriginal = false;
                                break;
                            }
                        }
                    }

                    List<String> nearestNodes = findNearestNodesTo(replacementEntry.getKey(), nodeName, replacementEntry.getValue(), oldNames, 2);
                    //if the original isn't in the closest top 2 nodes, then we shouldn't replace it
                    if (nodesToReplaceInputNamesWith.size() > 1) {
                        if (!nearestNodes.contains(replacementEntry.getKey())) {
                            shouldBeOriginal = false;
                        }
                    }

                    //layers that contain an already processed
                    //node as an input need modification
                    if (shouldBeOriginal) {
                        processed.add(nodeName);
                        continue;
                    }

                    //replace whatever the final input name is that was last
                    kerasLayer.getInboundLayerNames().set(kerasLayer.getInboundLayerNames()
                            .indexOf(replacementEntry.getKey()), nearestNodes.get(0));

                    processed.add(nodeName);


                }
            }
        }


        layers.clear();
        for(KerasLayer kerasLayer : layersOrdered) {
            layers.put(kerasLayer.getLayerName(),kerasLayer);
        }

        return new Pair<>(layers, layersOrdered);
    }

    List<String> findNearestNodesTo(String original,String target,List<String> targetedNodes,List<String> topoSortNodes,int k) {
        int idx = topoSortNodes.indexOf(target);
        Counter<String> rankByDistance = new Counter<>();

        for(int i = 0; i < targetedNodes.size(); i++) {
            int currIdx = topoSortNodes.indexOf(targetedNodes.get(i));
            int diff = Math.abs(currIdx - idx);
            //note we want the top k ranked by the least
            rankByDistance.incrementCount(targetedNodes.get(i),-diff);
        }

        int currIdx = topoSortNodes.indexOf(original);
        int diff = Math.abs(currIdx - idx);
        //note we want the top k ranked by the least
        rankByDistance.incrementCount(original,-diff);
        rankByDistance.keepTopNElements(k);
        return rankByDistance.keySetSorted();
    }

    Map<String, Object> getOptimizerConfig(Map<String, Object> trainingConfig) throws InvalidKerasConfigurationException{
        if (!trainingConfig.containsKey(config.getOptimizerConfig()))
            throw new InvalidKerasConfigurationException("Field "
                    + config.getOptimizerConfig() + " missing from layer config");
        return (Map<String, Object>) trainingConfig.get(config.getOptimizerConfig());
    }

    /**
     * Helper method called from constructor. Incorporate training configuration details into model.
     * Includes loss function, optimization details, etc.
     *
     * @param trainingConfigJson JSON containing Keras training configuration
     * @throws IOException                            IO exception
     * @throws InvalidKerasConfigurationException     Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    void importTrainingConfiguration(String trainingConfigJson)
            throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        Map<String, Object> trainingConfig = KerasModelUtils.parseJsonString(trainingConfigJson);

        Map<String, Object> optimizerConfig = getOptimizerConfig(trainingConfig);
        this.optimizer = KerasOptimizerUtils.mapOptimizer(optimizerConfig);

        /* Add loss layers for each loss function. */
        List<KerasLayer> lossLayers = new ArrayList<>();
        if (!trainingConfig.containsKey(config.getTrainingLoss()))
            throw new InvalidKerasConfigurationException("Could not determine training loss function (no "
                    + config.getTrainingLoss() + " field found in training config)");
        Object kerasLossObj = trainingConfig.get(config.getTrainingLoss());

        if (kerasLossObj instanceof String) {
            String kerasLoss = (String) kerasLossObj;
            for (String outputLayerName : this.outputLayerNames)
                lossLayers.add(new KerasLoss(outputLayerName + "_loss", outputLayerName, kerasLoss));
        } else if (kerasLossObj instanceof Map) {
            Map<String, Object> kerasLossMap = (Map<String, Object>) kerasLossObj;
            for (String outputLayerName : kerasLossMap.keySet()) {
                Object kerasLoss = kerasLossMap.get(outputLayerName);
                if (kerasLoss instanceof String)
                    lossLayers.add(new KerasLoss(outputLayerName + "_loss", outputLayerName, (String) kerasLoss));
                else
                    throw new InvalidKerasConfigurationException("Unknown Keras loss " + kerasLoss.toString());
            }
        }
        this.outputLayerNames.clear();

        /* Add loss layers to output layer list and layer graph. */
        for (KerasLayer lossLayer : lossLayers) {
            this.layersOrdered.add(lossLayer);
            this.layers.put(lossLayer.getLayerName(), lossLayer);
            this.outputLayerNames.add(lossLayer.getLayerName());
        }
    }

    /**
     * Helper method called from constructor. Infers and records output type
     * for every layer.
     */
    Map<String, InputType> inferOutputTypes(int[] inputShape)
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        Map<String, InputType> outputTypes = new HashMap<>();
        int kerasLayerIdx = 0;
        for (KerasLayer layer : this.layersOrdered) {
            InputType outputType;
            if (layer instanceof KerasInput) {
                if (inputShape != null && layer.inputShape == null) {
                    layer.inputShape = inputShape;
                }

                KerasInput kerasInput = (KerasInput) layer;
                Layer layer1 = layersOrdered.get(kerasLayerIdx + 1).layer;
                //no dim order, try to pull it from the next layer if there is one
                if(layer1 != null && ConvolutionUtils.layerHasConvolutionLayout(layer1)) {
                    CNN2DFormat formatForLayer = ConvolutionUtils.getFormatForLayer(layer1);
                    if(formatForLayer == CNN2DFormat.NCHW) {
                        dimOrder = KerasLayer.DimOrder.THEANO;
                    }  else if(formatForLayer == CNN2DFormat.NHWC) {
                        dimOrder = KerasLayer.DimOrder.TENSORFLOW;
                    } else {
                        dimOrder = KerasLayer.DimOrder.NONE;
                    }
                } else if(layer1 != null && Convolution3DUtils.layerHasConvolution3DLayout(layer1)) {
                    Convolution3D.DataFormat dataFormat = Convolution3DUtils.getFormatForLayer(layer1);
                    if(dataFormat == Convolution3D.DataFormat.NCDHW) {
                        dimOrder = KerasLayer.DimOrder.THEANO;
                    } else if(dataFormat == Convolution3D.DataFormat.NDHWC) {
                        dimOrder = KerasLayer.DimOrder.TENSORFLOW;
                    } else {
                        dimOrder = KerasLayer.DimOrder.NONE;

                    }
                }  else if(KerasRnnUtils.isRnnLayer(layersOrdered.get(kerasLayerIdx + 1))) {
                    if(kerasInput.inputShape == null)
                        kerasInput.inputShape =  layersOrdered.get(kerasLayerIdx + 1).inputShape;
                }

                if(dimOrder != null)
                    layer.setDimOrder(dimOrder);
                outputType = layer.getOutputType();
                this.truncatedBPTT = ((KerasInput) layer).getTruncatedBptt();
            } else {
                List<InputType> inputTypes = new ArrayList<>();
                int i = 0;
                for (String inboundLayerName : layer.getInboundLayerNames())
                    if(outputTypes.containsKey(inboundLayerName))
                        inputTypes.add(outputTypes.get(inboundLayerName));
                outputType = layer.getOutputType(inputTypes.toArray(new InputType[1]));
            }
            outputTypes.put(layer.getLayerName(), outputType);
            kerasLayerIdx++;
        }

        return outputTypes;
    }

    /**
     * Configure a ComputationGraph from this Keras Model configuration.
     *
     * @return ComputationGraph
     */
    public ComputationGraphConfiguration getComputationGraphConfiguration()
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        if (!this.className.equals(config.getFieldClassNameModel())
                && !this.className.equals(config.getFieldClassNameSequential())
                && !this.className.equals(config.getFieldNameClassFunctional()))
            throw new InvalidKerasConfigurationException(
                    "Keras model class name " + this.className + " incompatible with ComputationGraph");
        NeuralNetConfiguration.Builder modelBuilder = new NeuralNetConfiguration.Builder();

        if (optimizer != null) {
            modelBuilder.updater(optimizer);
        }

        Map<String,List<String>> outputs = new HashMap<>();
        for (KerasLayer layer : Lists.reverse(this.layersOrdered)) {
            for(String input : layer.getInboundLayerNames()) {
                if(!outputs.containsKey(input)) {
                    outputs.put(input,new ArrayList<String>());
                }

                outputs.get(input).add(layer.getLayerName());
            }
        }

        ComputationGraphConfiguration.GraphBuilder graphBuilder = modelBuilder.graphBuilder();
        // NOTE: normally this is disallowed in DL4J. However, in Keras you can create disconnected graph vertices.
        // The responsibility for doing this correctly is that of the Keras user.
        graphBuilder.allowDisconnected(true);


        /* Build String array of input layer names, add to ComputationGraph. */
        String[] inputLayerNameArray = new String[this.inputLayerNames.size()];
        this.inputLayerNames.toArray(inputLayerNameArray);
        graphBuilder.addInputs(inputLayerNameArray);

        /* Build InputType array of input layer types, add to ComputationGraph. */
        List<InputType> inputTypeList = new ArrayList<>();
        List<InputType> initialInputTypes = new ArrayList<>();
        for (String inputLayerName : this.inputLayerNames) {
            this.layers.get(inputLayerName);
            inputTypeList.add(this.layers.get(inputLayerName).getOutputType());

        }


        /* Build String array of output layer names, add to ComputationGraph. */
        String[] outputLayerNameArray = new String[this.outputLayerNames.size()];
        this.outputLayerNames.toArray(outputLayerNameArray);
        graphBuilder.setOutputs(outputLayerNameArray);

        Map<String, InputPreProcessor> preprocessors = new HashMap<>();
        int idx = 0;
        /* Add layersOrdered one at a time. */
        for (KerasLayer layer : this.layersOrdered) {
            /* Get inbound layer names. */
            List<String> inboundLayerNames = layer.getInboundLayerNames();
            String[] inboundLayerNamesArray = new String[inboundLayerNames.size()];
            inboundLayerNames.toArray(inboundLayerNamesArray);

            List<InputType> inboundTypeList = new ArrayList<>();

            /* Get inbound InputTypes and InputPreProcessor, if necessary. */
            if(!inboundLayerNames.isEmpty()) {
                InputType[] inputTypes2 = new InputType[inboundLayerNames.size()];
                int inboundIdx = 0;
                for (String layerName : inboundLayerNames) {
                    KerasLayer prevLayer = layers.get(layerName);
                    if(prevLayer.isInputPreProcessor()) {
                        InputType inputType = this.outputTypes.get(layerName);
                        InputPreProcessor preprocessor = prevLayer.getInputPreprocessor(inputType);
                        KerasModelUtils.setDataFormatIfNeeded(preprocessor,layer);
                        InputType outputType = preprocessor.getOutputType(inputType);
                        inputTypes2[inboundIdx] = outputType;
                        inboundIdx++;
                    }
                    else {
                        InputType inputType = this.outputTypes.get(layerName);
                        inputTypes2[inboundIdx] = inputType;
                        inboundIdx++;
                    }

                    if(outputTypes.containsKey(layerName))
                        inboundTypeList.add(this.outputTypes.get(layerName));
                }

            }

            InputType[] inboundTypeArray = new InputType[inboundTypeList.size()];
            inboundTypeList.toArray(inboundTypeArray);
            InputPreProcessor preprocessor = layer.getInputPreprocessor(inboundTypeArray);
            //don't add pre processor if there isn't anymore output, edge case for final layer
            if(idx == layersOrdered.size() - 1) {
                preprocessor = null;
            }
            if (layer.isLayer()) {
                if (preprocessor != null)
                    preprocessors.put(layer.getLayerName(), preprocessor);
                graphBuilder.addLayer(layer.getLayerName(), layer.getLayer(), inboundLayerNamesArray);
            } else if (layer.isVertex()) { // Ignore "preprocessor" layers for now
                if (preprocessor != null)
                    preprocessors.put(layer.getLayerName(), preprocessor);
                graphBuilder.addVertex(layer.getLayerName(), layer.getVertex(), inboundLayerNamesArray);
            } else if (layer.isInputPreProcessor()) {
                if (preprocessor == null)
                    throw new UnsupportedKerasConfigurationException("Layer " + layer.getLayerName()
                            + " could not be mapped to Layer, Vertex, or InputPreProcessor");
                graphBuilder.addVertex(layer.getLayerName(), new PreprocessorVertex(preprocessor),
                        inboundLayerNamesArray);
            }

            if(layer instanceof KerasInput) {
                initialInputTypes.add(this.outputTypes.get(layer.layerName));
            }

            idx++;
        }
        graphBuilder.setInputPreProcessors(preprocessors);

        /* Whether to use standard backprop (or BPTT) or truncated BPTT. */
        if (this.useTruncatedBPTT && this.truncatedBPTT > 0)
            graphBuilder.backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(truncatedBPTT)
                    .tBPTTBackwardLength(truncatedBPTT);
        else
            graphBuilder.backpropType(BackpropType.Standard);

        ComputationGraphConfiguration build = graphBuilder.build();
        //note we don't forcibly over ride inputs when doing keras import. They are already set.
        build.addPreProcessors(false,false,initialInputTypes.toArray(new InputType[initialInputTypes.size()]));
        return build;
    }

    /**
     * Build a ComputationGraph from this Keras Model configuration and import weights.
     *
     * @return ComputationGraph
     */
    public ComputationGraph getComputationGraph()
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        return getComputationGraph(true);
    }

    /**
     * Build a ComputationGraph from this Keras Model configuration and (optionally) import weights.
     *
     * @param importWeights whether to import weights
     * @return ComputationGraph
     */
    public ComputationGraph getComputationGraph(boolean importWeights)
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        ComputationGraph model = new ComputationGraph(getComputationGraphConfiguration());
        model.init();
        if (importWeights)
            model = (ComputationGraph) KerasModelUtils.copyWeightsToModel(model, this.layers);
        return model;
    }
}
