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

package org.nd4j.linalg.nativ;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.samediff.serde.FlatBuffersMapper;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.ops.BaseBroadcastOp;
import org.nd4j.linalg.api.ops.BaseIndexAccumulation;
import org.nd4j.linalg.api.ops.BaseReduceFloatOp;
import org.nd4j.linalg.api.ops.BaseScalarOp;
import org.nd4j.linalg.api.ops.BaseTransformSameOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.api.ops.impl.summarystats.Variance;
import org.nd4j.linalg.api.ops.random.BaseRandomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.common.primitives.Pair;
import org.nd4j.nativeblas.NativeOpsHolder;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@NativeTag
public class OpsMappingTests extends BaseNd4jTestWithBackends {


    @Override
    public char ordering(){
        return 'c';
    }

    @Override
    public long getTimeoutMilliseconds() {
        return 360000L;     //Can be very slow on some CI machines (PPC)
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testLegacyOpsMapping(Nd4jBackend backend) {
        Nd4j.create(1);

        val str = NativeOpsHolder.getInstance().getDeviceNativeOps().getAllOperations().replaceAll("simdOps::","").replaceAll("randomOps::","");

        val missing = new ArrayList<String>();

        //parsing individual groups first

        val groups = str.split(">>");
        for (val group: groups) {
            val line = group.split(" ");
            val bt = Integer.valueOf(line[0]).byteValue();
            val ops = line[1].split("<<");

            val type = FlatBuffersMapper.getTypeFromByte(bt);
            val list = getOperations(type);

            for (val op: ops) {
                val args = op.split(":");
                val hash = Long.valueOf(args[0]).longValue();
                val opNum = Long.valueOf(args[1]).longValue();
                val name = args[2];

                //log.info("group: {}; hash: {}; name: {};", SameDiff.getTypeFromByte(bt), hash, name);
                val needle = new Operation(type == Op.Type.CUSTOM ? -1 : opNum, name.toLowerCase());
                if (!opMapped(list, needle))
                    missing.add(type.toString() + " " + name);

            }
        }

        if (missing.size() > 0) {

            log.info("{} ops missing!", missing.size());
            log.info("{}", missing);
            //assertTrue(false);
        }
    }

    protected boolean opMapped(List<Operation> haystack, Operation needle) {
        for (val c: haystack) {
            if (needle.getFirst().longValue() == -1L) {
                if (c.getSecond().equalsIgnoreCase(needle.getSecond()))
                    return true;
            } else {
                if (c.getFirst().longValue() == needle.getFirst().longValue())
                    return true;
            }
        }

        return false;
    }

    protected void addOperation(Class<? extends DifferentialFunction> clazz, List<Operation> list) {
        if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface())
            return;

        try {
            DifferentialFunction node = clazz.newInstance();
            if (node instanceof DynamicCustomOp) {
                list.add(new Operation(-1L, node.opName().toLowerCase()));
                list.add(new Operation(-1L, node.tensorflowName().toLowerCase()));
            } else {
                val op = new Operation(Long.valueOf(node.opNum()), node.opName());
                list.add(op);
            }
        } catch (UnsupportedOperationException e) {
            //
        } catch (NoOpNameFoundException e) {
            //
        } catch (InstantiationException e) {
            //
        } catch (Exception e) {
            log.info("Failed on [{}]", clazz.getSimpleName());
            throw new RuntimeException(e);
        }
    }

    protected List<Operation> getOperations(@NonNull Op.Type type) {
        val list = new ArrayList<Operation>();

        Reflections f = new Reflections(new ConfigurationBuilder()
                .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix("org.nd4j.*")).exclude("^(?!.*\\.class$).*$"))
                .setUrls(ClasspathHelper.forPackage("org.nd4j")).setScanners(new SubTypesScanner()));


        switch (type) {
            case SUMMARYSTATS: {
                Set<Class<? extends Variance>> clazzes = f.getSubTypesOf(Variance.class);

                for (Class<? extends DifferentialFunction> clazz : clazzes)
                    addOperation(clazz, list);
            }
            break;
            case RANDOM: {
                Set<Class<? extends BaseRandomOp>> clazzes = f.getSubTypesOf(BaseRandomOp.class);

                for (Class<? extends DifferentialFunction> clazz : clazzes)
                    addOperation(clazz, list);
            }
            break;
            case INDEXREDUCE: {
                Set<Class<? extends BaseIndexAccumulation>> clazzes = f.getSubTypesOf(BaseIndexAccumulation.class);

                for (Class<? extends DifferentialFunction> clazz : clazzes)
                    addOperation(clazz, list);
            }
            break;
            case REDUCE3:
            case REDUCE_FLOAT: {
                Set<Class<? extends BaseReduceFloatOp>> clazzes = f.getSubTypesOf(BaseReduceFloatOp.class);

                for (Class<? extends DifferentialFunction> clazz : clazzes)
                    addOperation(clazz, list);
            }
            break;
            case BROADCAST: {
                Set<Class<? extends BaseBroadcastOp>> clazzes = f.getSubTypesOf(BaseBroadcastOp.class);

                for (Class<? extends DifferentialFunction> clazz : clazzes)
                    addOperation(clazz, list);
            }
            break;
            case SCALAR: {
                Set<Class<? extends BaseScalarOp>> clazzes = f.getSubTypesOf(BaseScalarOp.class);

                for (Class<? extends DifferentialFunction> clazz : clazzes)
                    addOperation(clazz, list);
            }
            break;
            case PAIRWISE:
            case TRANSFORM_SAME: {
                Set<Class<? extends BaseTransformSameOp>> clazzes = f.getSubTypesOf(BaseTransformSameOp.class);

                for (Class<? extends DifferentialFunction> clazz : clazzes)
                    addOperation(clazz, list);
            }
            break;
            case CUSTOM: {
                Set<Class<? extends DynamicCustomOp>> clazzes = f.getSubTypesOf(DynamicCustomOp.class);

                for (Class<? extends DifferentialFunction> clazz : clazzes) {
                    if (clazz.getSimpleName().equalsIgnoreCase("dynamiccustomop"))
                        continue;

                    addOperation(clazz, list);
                }
            }
            break;
            //default:
            //    throw new ND4JIllegalStateException("Unknown operation type: " + type);
        }


        log.info("Group: {}; List size: {}", type, list.size());

        return list;
    }


    protected static class Operation extends Pair<Long, String> {
        protected Operation(Long opNum, String name) {
            super(opNum, name);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Operation))
                return false;

            Operation op = (Operation) o;

            return op.key.equals(this.key);
        }
    }
}
