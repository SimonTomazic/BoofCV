/*
 * Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
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

package boofcv.alg.filter.convolve.noborder;

import boofcv.alg.filter.convolve.TestConvolveImageBox;
import boofcv.alg.misc.GImageMiscOps;
import boofcv.core.image.GeneralizedImageOps;
import boofcv.struct.image.ImageSingleBand;
import boofcv.testing.BoofTesting;
import boofcv.testing.CompareEquivalentFunctions;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Random;

/**
 * @author Peter Abeles
 */
public class TestImplConvolveBox extends CompareEquivalentFunctions {

	Random rand = new Random(0xFF);

	static int width = 10;
	static int height = 12;
	static int kernelRadius = 2;

	public TestImplConvolveBox() {
		super(ImplConvolveBox.class, ConvolveImageStandard.class);
	}

	@Test
	public void compareToStandard() {
		performTests(8);
	}

	@Override
	protected boolean isTestMethod(Method m) {
		Class<?> params[] = m.getParameterTypes();

		if( params.length != 4)
			return false;

		return ImageSingleBand.class.isAssignableFrom(params[0]);
	}

	@Override
	protected boolean isEquivalent(Method candidate, Method validation) {

		Class<?> v[] = candidate.getParameterTypes();
		Class<?> c[] = validation.getParameterTypes();

		if( v.length != 4 )
			return false;

		if( !candidate.getName().equals(validation.getName()))
			return false;

		return c[0] == v[1] && c[1] == v[2];
	}

	@Override
	protected Object[][] createInputParam(Method candidate, Method validation) {

		Class c[] = candidate.getParameterTypes();

		ImageSingleBand input = GeneralizedImageOps.createSingleBand(c[0], width, height);
		ImageSingleBand output = GeneralizedImageOps.createSingleBand(c[1], width, height);

		GImageMiscOps.fillUniform(input, rand, 0, 20);

		Object[][] ret = new Object[2][];
		ret[0] = new Object[]{input,output,kernelRadius,true};
		ret[1] = new Object[]{input,output,kernelRadius,false};

		return ret;
	}

	@Override
	protected Object[] reformatForValidation(Method m, Object[] targetParam) {
		Class<?> params[] = m.getParameterTypes();
		Object kernel = TestConvolveImageBox.createTableKernel(params[0],kernelRadius,rand);

		ImageSingleBand output = (ImageSingleBand)((ImageSingleBand)targetParam[1]).clone();

		return new Object[]{kernel,targetParam[0],output,targetParam[3]};
	}

	@Override
	protected void compareResults(Object targetResult, Object[] targetParam, Object validationResult, Object[] validationParam) {
		ImageSingleBand expected = (ImageSingleBand)validationParam[2];
		ImageSingleBand found = (ImageSingleBand)targetParam[1];

		BoofTesting.assertEquals(expected, found, 1e-4);
	}
}
