/*
 * Copyright (c) 2011-2012, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.geo.trifocal;

import boofcv.alg.geo.PerspectiveOps;
import boofcv.numerics.optimization.UnconstrainedLeastSquares;
import boofcv.numerics.optimization.functions.FunctionNtoM;
import boofcv.numerics.optimization.impl.UtilOptimize;
import boofcv.struct.geo.AssociatedTriple;
import georegression.struct.point.Point3D_F64;
import org.ejml.data.DenseMatrix64F;

import java.util.List;

/**
 * <p>
 * Initially computes the trifocal tensor using the linear method {@link TrifocalLinearPoint7}, but
 * then iteratively refines the solution to minimize algebraic error by adjusting the two epipoles.
 * The solution will enforce all the constraints and be geometrically valid. See page 395 in [1].
 * </p>
 *
 * <p>
 * References:
 * <ul>
 * <li> R. Hartley, and A. Zisserman, "Multiple View Geometry in Computer Vision", 2nd Ed, Cambridge 2003 </li>
 * </ul>
 * </p>
 *
 * @author Peter Abeles
 */
public class TrifocalAlgebraicPoint7 extends TrifocalLinearPoint7 {

	// optimization algorithm of function
	private UnconstrainedLeastSquares optimizer;
	private ErrorFunction errorFunction = new ErrorFunction();

	// optimization parameters
	private int maxIterations;
	private double ftol;
	private double gtol;

	// storage for epipoles being optimised
	private double param[] = new double[6];

	/**
	 * Configures optimization algorithms
	 *
	 * @param optimizer Which least squares minimizer should be used.
	 * @param maxIterations Maximum number of iterations it will optimize for
	 * @param ftol Convergence tolerance.  See {@link UnconstrainedLeastSquares} for details.
	 * @param gtol Convergence tolerance.  See {@link UnconstrainedLeastSquares} for details.
	 */
	public TrifocalAlgebraicPoint7(UnconstrainedLeastSquares optimizer,
								   int maxIterations, double ftol,
								   double gtol) {
		this.optimizer = optimizer;
		this.maxIterations = maxIterations;
		this.ftol = ftol;
		this.gtol = gtol;
	}

	@Override
	public boolean process(List<AssociatedTriple> observations) {
		if( observations.size() < 7 )
			throw new IllegalArgumentException("At least 7 correspondences must be provided");

		// compute normalization to reduce numerical errors
		PerspectiveOps.computeNormalization(observations,N1,N2,N3);
//		CommonOps.setIdentity(N1);
//		CommonOps.setIdentity(N2);
//		CommonOps.setIdentity(N3);

		// compute solution in normalized pixel coordinates
		createLinearSystem(observations);

		// solve for the trifocal tensor
		solveLinearSystem();

		// minimize geometric error
		optimizeSolution();

		// undo normalization
		removeNormalization();

		return true;
	}

	/**
	 * Minimize algebraic error using LM.  The two epipoles are the parameters being optimized.
	 */
	private void optimizeSolution() {
		extractEpipoles.process(solutionN, e2, e3);

		// encode the parameters being optimized
		param[0] = e2.x; param[1] = e2.y; param[2] = e2.z;
		param[3] = e3.x; param[4] = e3.y; param[5] = e3.z;

		// adjust the error function for the current inputs
		errorFunction.init();

//		DenseMatrix64F errors = new DenseMatrix64F(A.numRows,1);
//		errorFunction.process(param,errors.data);
//
//		DenseMatrix64F errors2 = new DenseMatrix64F(A.numRows,1);
//		CommonOps.mult(A,vectorizedSolution,errors2);

		// set up the optimization algorithm
		optimizer.setFunction(errorFunction,null);
		optimizer.initialize(param,gtol,ftol);

		// optimize until convergence or the maximum number of iterations
		UtilOptimize.process(optimizer, maxIterations);

		// get the results and compute the trifocal tensor
		double found[] = optimizer.getParameters();
		paramToEpipoles(found,e2,e3);

		enforce.process(e2,e3,A);
		enforce.extractSolution(solutionN);
	}

	private static void paramToEpipoles(double[] found , Point3D_F64 e2 , Point3D_F64 e3 ) {
		e2.x = found[0]; e2.y = found[1]; e2.z = found[2];
		e3.x = found[3]; e3.y = found[4]; e3.z = found[5];
	}

	/**
	 * Computes the algebraic error for a given set of epipoles
	 */
	private class ErrorFunction implements FunctionNtoM {

		Point3D_F64 e2 = new Point3D_F64();
		Point3D_F64 e3 = new Point3D_F64();

		DenseMatrix64F errors = new DenseMatrix64F(1,1);

		public void init() {
			errors.numRows = A.numRows;
			errors.numCols = 1;
		}

		@Override
		public int getN() {
			return 6;
		}

		@Override
		public int getM() {
			return A.numRows;
		}

		@Override
		public void process(double[] input, double[] output) {
			paramToEpipoles(input, e2, e3);

			enforce.process(e2,e3,A);
			errors.data = output;
			enforce.computeErrorVector(A,errors);
		}
	}
}
