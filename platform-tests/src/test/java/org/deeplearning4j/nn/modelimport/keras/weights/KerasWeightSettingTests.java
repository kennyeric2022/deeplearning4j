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

package org.deeplearning4j.nn.modelimport.keras.weights;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.modelimport.keras.KerasLayer;
import org.deeplearning4j.nn.modelimport.keras.KerasModel;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.layers.convolutional.KerasSpaceToDepth;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.common.resources.Resources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@Tag(TagNames.FILE_IO)
@Tag(TagNames.KERAS)
@NativeTag
public class KerasWeightSettingTests extends BaseDL4JTest {


    @Override
    public long getTimeoutMilliseconds() {
        return 9999999L;
    }



    @Test
    public void testOtherWeights() throws Exception {
        File modelFile = Resources.asFile("modelimport/keras/weights/issue_9560.h5");
        MultiLayerNetwork multiLayerNetwork = KerasModelImport.importKerasSequentialModelAndWeights(modelFile.getAbsolutePath());
        INDArray output = multiLayerNetwork.output(Nd4j.ones(1,2048,1));
        INDArray params = multiLayerNetwork.params();
        assertEquals(267590,params.length());
        assertArrayEquals(new long[] {1,10},output.shape());
    }


    @Test
    public void testWeights() throws Exception {
        File file = Resources.asFile("modelimport/keras/weights/keras_2.7_issue.h5");
        MultiLayerNetwork multiLayerNetwork = KerasModelImport.importKerasSequentialModelAndWeights(file.getAbsolutePath());
        System.out.println(multiLayerNetwork.summary());
        INDArray output = multiLayerNetwork.output(Nd4j.ones(1, 128, 76));
        assertArrayEquals(new long[]{1,2},output.shape());

    }

    @Test
    public void testSimpleLayersWithWeights(@TempDir Path tempDir) throws Exception {
        int[] kerasVersions = new int[]{1, 2};
        String[] backends = new String[]{"tensorflow", "theano"};

        for (int version : kerasVersions) {
            for (String backend : backends) {
                String densePath = "modelimport/keras/weights/dense_" + backend + "_" + version + ".h5";
                importDense(tempDir,densePath);

                String conv2dPath = "modelimport/keras/weights/conv2d_" + backend + "_" + version + ".h5";
                importConv2D(tempDir,conv2dPath);

                if (version == 2 && backend.equals("tensorflow")) { // TODO should work for theano
                    String conv2dReshapePath = "modelimport/keras/weights/conv2d_reshape_"
                            + backend + "_" + version + ".h5";
                    System.out.println(backend + "_" + version);
                    importConv2DReshape(tempDir,conv2dReshapePath);
                }

                if (version == 2) {
                    String conv1dFlattenPath = "modelimport/keras/weights/embedding_conv1d_flatten_"
                            + backend + "_" + version + ".h5";
                    importConv1DFlatten(tempDir,conv1dFlattenPath);
                }

                String lstmPath = "modelimport/keras/weights/lstm_" + backend + "_" + version + ".h5";
                importLstm(tempDir,lstmPath);

                String embeddingLstmPath = "modelimport/keras/weights/embedding_lstm_"
                        + backend + "_" + version + ".h5";
                importEmbeddingLstm(tempDir,embeddingLstmPath);


                if (version == 2) {
                    String embeddingConv1dExtendedPath = "modelimport/keras/weights/embedding_conv1d_extended_"
                            + backend + "_" + version + ".h5";
                    importEmbeddingConv1DExtended(tempDir,embeddingConv1dExtendedPath);
                }

                if (version == 2) {
                    String embeddingConv1dPath = "modelimport/keras/weights/embedding_conv1d_"
                            + backend + "_" + version + ".h5";
                    importEmbeddingConv1D(tempDir,embeddingConv1dPath);
                }

                String simpleRnnPath = "modelimport/keras/weights/simple_rnn_" + backend + "_" + version + ".h5";
                importSimpleRnn(tempDir,simpleRnnPath);

                String bidirectionalLstmPath = "modelimport/keras/weights/bidirectional_lstm_"
                        + backend + "_" + version + ".h5";
                importBidirectionalLstm(tempDir,bidirectionalLstmPath);

                String bidirectionalLstmNoSequencesPath =
                        "modelimport/keras/weights/bidirectional_lstm_no_return_sequences_"
                                + backend + "_" + version + ".h5";
                importBidirectionalLstm(tempDir,bidirectionalLstmNoSequencesPath);

                if (version == 2 && backend.equals("tensorflow")) {
                    String batchToConv2dPath = "modelimport/keras/weights/batch_to_conv2d_"
                            + backend + "_" + version + ".h5";
                    importBatchNormToConv2D(tempDir,batchToConv2dPath);
                }

                if (backend.equals("tensorflow") && version == 2) { // TODO should work for theano
                    String simpleSpaceToBatchPath = "modelimport/keras/weights/space_to_depth_simple_"
                            + backend + "_" + version + ".h5";
                    importSimpleSpaceToDepth(tempDir,simpleSpaceToBatchPath);
                }

                if (backend.equals("tensorflow") && version == 2) {
                    String graphSpaceToBatchPath = "modelimport/keras/weights/space_to_depth_graph_"
                            + backend + "_" + version + ".h5";
                    importGraphSpaceToDepth(tempDir,graphSpaceToBatchPath);
                }

                if (backend.equals("tensorflow") && version == 2) {
                    String sepConvPath = "modelimport/keras/weights/sepconv2d_" + backend + "_" + version + ".h5";
                    importSepConv2D(tempDir,sepConvPath);
                }
            }
        }
    }

    private void logSuccess(String modelPath) {
        log.info("***** Successfully imported " + modelPath);
    }

    private void importDense(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, true);

        INDArray weights = model.getLayer(0).getParam("W");
        val weightShape = weights.shape();
        assertEquals(4, weightShape[0]);
        assertEquals(6, weightShape[1]);

        INDArray bias = model.getLayer(0).getParam("b");
        assertEquals(6, bias.length());
        logSuccess(modelPath);
    }

    private void importSepConv2D(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);

        INDArray depthWeights = model.getLayer(0).getParam("W");
        val depthWeightShape = depthWeights.shape();

        long depthMult = 2;
        long kernel = 3;
        long nIn = 5;
        long nOut = 6;

        assertEquals(depthMult, depthWeightShape[0]);
        assertEquals(nIn, depthWeightShape[1]);
        assertEquals(kernel, depthWeightShape[2]);
        assertEquals(kernel, depthWeightShape[3]);

        INDArray weights = model.getLayer(0).getParam("pW");
        val weightShape = weights.shape();


        assertEquals(nOut, weightShape[0]);
        assertEquals(nIn * depthMult, weightShape[1]);
        assertEquals(1, weightShape[2]);
        assertEquals(1, weightShape[3]);

        INDArray bias = model.getLayer(0).getParam("b");
        assertEquals(6, bias.length());

        INDArray input = Nd4j.ones(1, 3, 4, 5);     //NHWC
        INDArray output = model.output(input);

        assertArrayEquals(new long[] {1, 1, 2, 6}, output.shape()); //NHWC

        logSuccess(modelPath);
    }

    private void importConv2D(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);

        INDArray weights = model.getLayer(0).getParam("W");
        val weightShape = weights.shape();
        assertEquals(6, weightShape[0]);
        assertEquals(5, weightShape[1]);
        assertEquals(3, weightShape[2]);
        assertEquals(3, weightShape[3]);

        INDArray bias = model.getLayer(0).getParam("b");
        assertEquals(6,bias.length());
        logSuccess(modelPath);
    }


    private void importConv2DReshape(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);


        int nOut = 12;
        int mb = 10;
        ;
        int[] inShape = new int[]{5, 5, 5};
        INDArray input = Nd4j.zeros(mb, inShape[0], inShape[1], inShape[2]);
        INDArray output = model.output(input);
        assertArrayEquals(new long[]{mb, nOut}, output.shape());
        logSuccess(modelPath);
    }

    private void importConv1DFlatten(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);

        int nOut = 6;
        int inputLength = 10;
        int mb = 42;
        int kernel = 3;

        INDArray input = Nd4j.zeros(mb, inputLength);
        INDArray output = model.output(input);
        if(modelPath.contains("tensorflow"))
            assertArrayEquals(new long[]{mb, inputLength - kernel + 1,  nOut}, output.shape());     //NWC
        else if(modelPath.contains("theano")) {
            assertArrayEquals(new long[]{mb, nOut,inputLength - kernel + 1}, output.shape());     //NCW

        }
        logSuccess(modelPath);
    }

    private void importBatchNormToConv2D(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);
        model.summary();
        logSuccess(modelPath);
    }

    private void importSimpleSpaceToDepth(Path tempDir,String modelPath) throws Exception {
        KerasLayer.registerCustomLayer("Lambda", KerasSpaceToDepth.class);
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);

        INDArray input = Nd4j.zeros(10, 6, 6, 4);
        INDArray output = model.output(input);
        assertArrayEquals(new long[]{10, 3, 3, 16}, output.shape());
        logSuccess(modelPath);
    }

    private void importGraphSpaceToDepth(Path tempDir,String modelPath) throws Exception {
        KerasLayer.registerCustomLayer("Lambda", KerasSpaceToDepth.class);
        ComputationGraph model = loadComputationalGraph(tempDir,modelPath, false);

//        INDArray input[] = new INDArray[]{Nd4j.zeros(10, 4, 6, 6), Nd4j.zeros(10, 16, 3, 3)};
        INDArray input[] = new INDArray[]{Nd4j.zeros(10, 6, 6, 4), Nd4j.zeros(10, 3, 3, 16)};
        INDArray[] output = model.output(input);
        log.info(Arrays.toString(output[0].shape()));
        assertArrayEquals(new long[]{10, 3, 3, 32}, output[0].shape());
        logSuccess(modelPath);
    }

    private void importLstm(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);
        model.summary();
        // TODO: check weights
        logSuccess(modelPath);
    }

    private void importEmbeddingLstm(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);

        int nIn = 4;
        int nOut = 6;
        int outputDim = 5;
        int inputLength = 10;
        int mb = 42;

        INDArray embeddingWeight = model.getLayer(0).getParam("W");
        val embeddingWeightShape = embeddingWeight.shape();
        assertEquals(nIn, embeddingWeightShape[0]);
        assertEquals(outputDim, embeddingWeightShape[1]);

        INDArray inEmbedding = Nd4j.zeros(mb, inputLength);
        INDArray output = model.output(inEmbedding);
        assertArrayEquals(new long[]{mb, inputLength, nOut}, output.shape());       //NWC format
        logSuccess(modelPath);
    }

    private void importEmbeddingConv1DExtended(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);
        logSuccess(modelPath);
    }

    private void importEmbeddingConv1D(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);

        int nIn = 4;
        int nOut = 6;
        int outputDim = 5;
        int inputLength = 10;
        int kernel = 3;
        int mb = 42;

        INDArray embeddingWeight = model.getLayer(0).getParam("W");
        val embeddingWeightShape = embeddingWeight.shape();
        assertEquals(nIn, embeddingWeightShape[0]);
        assertEquals(outputDim, embeddingWeightShape[1]);

        INDArray inEmbedding = Nd4j.zeros(mb, inputLength);
        INDArray output = model.output(inEmbedding);
        if(modelPath.contains("tensorflow"))
            assertArrayEquals(new long[]{mb, inputLength - kernel + 1, nOut}, output.shape());      //NWC
        else if(modelPath.contains("theano"))
            assertArrayEquals(new long[]{mb, nOut,inputLength - kernel + 1}, output.shape());      //NCC

        logSuccess(modelPath);
    }

    private void importSimpleRnn(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);
        model.summary();
        logSuccess(modelPath);
        // TODO: check weights
    }

    private void importBidirectionalLstm(Path tempDir,String modelPath) throws Exception {
        MultiLayerNetwork model = loadMultiLayerNetwork(tempDir,modelPath, false);
        model.summary();
        logSuccess(modelPath);
        // TODO: check weights
    }

    private MultiLayerNetwork loadMultiLayerNetwork(Path tempDir, String modelPath, boolean training) throws Exception {
        File modelFile = createTempFile(tempDir,"temp", ".h5");
        try(InputStream is = Resources.asStream(modelPath)) {
            Files.copy(is, modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return new KerasModel().modelBuilder().modelHdf5Filename(modelFile.getAbsolutePath())
                    .enforceTrainingConfig(training).buildSequential().getMultiLayerNetwork();
        }
    }

    private ComputationGraph loadComputationalGraph(Path tempDir,String modelPath, boolean training) throws Exception {
        File modelFile = createTempFile(tempDir,"temp", ".h5");
        try(InputStream is = Resources.asStream(modelPath)) {
            Files.copy(is, modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return new KerasModel().modelBuilder().modelHdf5Filename(modelFile.getAbsolutePath())
                    .enforceTrainingConfig(training).buildModel().getComputationGraph();
        }
    }

    private File createTempFile(Path tempDir,String prefix, String suffix) throws IOException {
        File createTempFile = Files.createTempFile(tempDir,prefix + "-" + System.nanoTime(),suffix).toFile();
        return createTempFile;
    }

}
