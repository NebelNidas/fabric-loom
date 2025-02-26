/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2020 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration.processors;

import java.io.File;

public interface JarProcessor {
	/**
	 * Returns a unique ID for this jar processor, containing all configuration details.
	 *
	 * <p>If the jar processor implementation class supports creating multiple jar processors with different effects,
	 * the needed configuration should also be included in this ID. Example: {@code path.to.MyJarProcessor#someOption}.
	 *
	 * @return the ID of this jar processor
	 */
	default String getId() {
		return getClass().getName();
	}

	void setup();

	/**
	 * Currently this is a destructive process that replaces the existing jar.
	 */
	void process(File file);

	/**
	 * Return true to make all jar processors run again, return false to use the existing results of jar processing.
	 */
	boolean isInvalid(File file);
}
