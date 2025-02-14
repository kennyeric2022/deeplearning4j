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

package org.nd4j.common.config;

public class ND4JEnvironmentVars {

    /**
     * Applicability: nd4j-native, when multiple backends are on classpath<br>
     * Description: Defines the priority that the CPU/Native backend should be loaded (or attempt to be loaded). If this
     * is set to a higher value than {@link #BACKEND_PRIORITY_GPU} (which has default value 100) the native backend
     * will be loaded in preference to the CUDA backend, when both are on the classpath. Default value: 0
     */
    public static final String BACKEND_PRIORITY_CPU = "BACKEND_PRIORITY_CPU";
    /**
     * Applicability: nd4j-cuda-xx, when multiple backends are on classpath<br>
     * Description: Defines the priority that the CUDA (GPU) backend should be loaded (or attempt to be loaded). If this
     * is set to a higher value than {@link #BACKEND_PRIORITY_CPU} (which has default value 0) the GPU backend
     * will be loaded in preference to the CUDA backend, when both are on the classpath. Default value: 100 - hence
     * by default, the CUDA backend will be loaded when both it and the CPU/native backend are on the classpath
     */
    public static final String BACKEND_PRIORITY_GPU = "BACKEND_PRIORITY_GPU";


    /**
     * Applicability: nd4j-aurora-xx, when multiple backends are on classpath<br>
     * Description: Defines the priority that the CUDA (GPU) backend should be loaded (or attempt to be loaded). If this
     * is set to a higher value than {@link #BACKEND_PRIORITY_CPU} (which has default value 0) the Aurora backend
     * will be loaded in preference to the AURORA backend, when both are on the classpath. Default value: 100 - hence
     * by default, the AURORA backend will be loaded when both it and the CPU/native backend are on the classpath
     */
    public static final String BACKEND_PRIORITY_AURORA = "BACKEND_PRIORITY_AURORA";

    /**
     * Applicability: always - but only if an ND4J backend cannot be found/loaded via standard ServiceLoader mechanisms<br>
     * Description: Set this environment variable to a set fully qualified JAR files to attempt to load before failing on
     * not loading a backend. JAR files should be semi-colon delimited; i.e., "/some/file.jar;/other/path.jar".
     * This should rarely be required in practice - for example, only in dynamic class loading/dynamic classpath scenarios<br>
     * For equivalent system property, see {@link ND4JSystemProperties#DYNAMIC_LOAD_CLASSPATH_PROPERTY} for the equivalent
     * system property (that will take precidence if both are set)
     */
    public static final String BACKEND_DYNAMIC_LOAD_CLASSPATH = "ND4J_DYNAMIC_LOAD_CLASSPATH";
    /**
     * Applicability: nd4j-native backend<br>
     * Description: Sets the number of OpenMP parallel threads for ND4J native operations (and also native BLAS libraries
     * such as Intel MKL and OpenBLAS).
     * By default, this will be set to the number of physical cores (i.e., excluding hyperthreading cores), which usually
     * provides optimal performance. Setting this to a larger value than the number of physical cores (for example, equal
     * to number of logical cores - i.e., setting to 16 on an 8-core + hypethreading processor) - can result in reduced
     * performance<br>
     * Note that if you have a significant number of parallel Java threads (for example, Spark or ParallelWrapper), or
     * you want to keep some cores free for other programs - you may want to reduce this value.
     *
     * @see #ND4J_SKIP_BLAS_THREADS
     */
    public static final String OMP_NUM_THREADS = "OMP_NUM_THREADS";
    /**
     * Applicability: nd4j-native backend<br>
     * Description: Skips the setting of the {@link #OMP_NUM_THREADS} property for ND4J ops. Note that this property
     * will usually still take effect for native BLAS libraries (MKL, OpenBLAS) even if this property is set
     */
    public static final String ND4J_SKIP_BLAS_THREADS = "ND4J_SKIP_BLAS_THREADS";
    /**
     * Applicability: nd4j-native backend<br>
     * Description: Whether build-in BLAS matrix multiplication (GEMM) should be used instead of the native BLAS
     * library such as MKL or OpenBLAS. This can have a noticable performance impact for these ops.
     * Note that this is typically only useful as a workaround (or test) for bugs in these underlying native libraries,
     * which are rare (but do occasionally occur on some platforms)
     */
    public static final String ND4J_FALLBACK = "ND4J_FALLBACK";
    /**
     * Applicability: nd4j-parameter-server<br>
     * Usage: A fallback for determining the local IP the parameter server, if other approaches fail to determine the
     * local IP
     */
    public static final String DL4J_VOID_IP = "DL4J_VOID_IP";
    /**
     * Applicability: nd4j-cuda-xx<br>
     * Description:
     */
    public static final String ND4J_CUDA_MAX_BLOCK_SIZE = "ND4J_CUDA_MAX_BLOCK_SIZE";
    /**
     * Applicability: nd4j-cuda-xx<br>
     * Description:
     */
    public static final String ND4J_CUDA_MIN_BLOCK_SIZE = "ND4J_CUDA_MIN_BLOCK_SIZE";
    /**
     * Applicability: nd4j-cuda-xx<br>
     * Description:
     */
    public static final String ND4J_CUDA_MAX_GRID_SIZE = "ND4J_CUDA_MAX_GRID_SIZE";

    /**
     * Applicability: nd4j-cuda-xx<br>
     * Description: This variable defines how many concurrent threads will be able to use same device. Keep in mind, this doesn't affect natural CUDA limitations
     */
    public static final String ND4J_CUDA_MAX_CONTEXTS = "ND4J_CUDA_MAX_CONTEXTS";

    /**
     * Applicability: nd4j-cuda-xx used on multi-GPU systems<br>
     * Description: If set, only a single GPU will be used by ND4J, even if multiple GPUs are available in the system
     */
    public static final String ND4J_CUDA_FORCE_SINGLE_GPU = "ND4J_CUDA_FORCE_SINGLE_GPU";
    /**
     * Applicability: nd4j-cuda-xx<br>
     * Description:
     */
    public static final String ND4J_CUDA_USE_PREALLOCATION = "ND4J_CUDA_USE_PREALLOCATION";
    /**
     * Applicability: nd4j-cuda-xx<br>
     * Description:
     */
    public static final String ND4J_CUDA_MAX_DEVICE_CACHE = "ND4J_CUDA_MAX_DEVICE_CACHE";
    /**
     * Applicability: nd4j-cuda-xx<br>
     * Description:
     */
    public static final String ND4J_CUDA_MAX_HOST_CACHE = "ND4J_CUDA_MAX_HOST_CACHE";
    /**
     * Applicability: nd4j-cuda-xx<br>
     * Description:
     */
    public static final String ND4J_CUDA_MAX_DEVICE_ALLOCATION = "ND4J_CUDA_MAX_DEVICE_ALLOCATION";

    /**
     * Applicability: nd4j-native
     */
    public static final String ND4J_MKL_FALLBACK = "ND4J_MKL_FALLBACK";

    public static final String ND4J_RESOURCES_CACHE_DIR = "ND4J_RESOURCES_CACHE_DIR";

    /**
     * Applicability: nd4j-native<br>
     * Description: Set to true to avoid logging AVX warnings (i.e., running generic x86 binaries on an AVX2 system)
     */
    public static final String ND4J_IGNORE_AVX = "ND4J_IGNORE_AVX";

    /**
     * This variable defines how many threads will be used in ThreadPool for parallel execution of linear algebra.
     * Default value: number of threads supported by this system.
     */
    public static final String SD_MAX_THREADS = "SD_MAX_THREADS";

    /**
     * This variable defines how many threads will be used for any 1 linear algebra operation.
     * Default value: number of threads supported by this system.
     */
    public static final String SD_MASTER_THREADS = "SD_MASTER_THREADS";

    /**
     * If set, this variable disables use of optimized platform helpers (i.e. mkldnn or cuDNN)
     */
    public static final String SD_FORBID_HELPERS = "SD_FORBID_HELPERS";

    /**
     * If set, this variables defines how much memory application is allowed to use off-heap.
     * PLEASE NOTE: this option is separate from JVM XMS/XMX options
     */
    public static final String SD_MAX_PRIMARY_BYTES = "SD_MAX_PRIMARY_BYTES";

    /**
     * If set, this variable defines how much memory application is allowed to use ON ALL computational devices COMBINED.
     */
    public static final String SD_MAX_SPECIAL_BYTES = "SD_MAX_SPECIAL_BYTES";

    /**
     * If set, this variable defines how much memory application is allowed to use on any one computational device
     */
    public static final String SD_MAX_DEVICE_BYTES = "SD_MAX_DEVICE_BYTES";

    private ND4JEnvironmentVars() {
    }
}
