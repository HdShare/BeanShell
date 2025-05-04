/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/


package bsh.classpath;

import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.DexFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import bsh.BshClassManager;
import bsh.classpath.BshClassPath.ClassSource;
import dalvik.system.InMemoryDexClassLoader;

/**
	A classloader which can load one or more classes from specified sources.
	Because the classes are loaded via a single classloader they change as a
	group and any versioning cross dependencies can be managed.
*/
public class DiscreteFilesClassLoader extends BshClassLoader
{
	/**
		Map of class sources which also implies our coverage space.
	*/
	ClassSourceMap map;

	public static class ClassSourceMap extends HashMap
	{
		public void put( String name, ClassSource source ) {
			super.put( name, source );
		}
		public ClassSource get( String name ) {
			return (ClassSource)super.get( name );
		}
	}

	public DiscreteFilesClassLoader(
		BshClassManager classManager, ClassSourceMap map )
	{
		super( classManager );
		this.map = map;
	}

	/**
	*/
	public Class findClass( String name ) throws ClassNotFoundException
	{
		// Load it if it's one of our classes
		ClassSource source = map.get( name );

		if ( source != null )
		{
			byte [] code = source.getCode( name );

			try {
				DexOptions dexOptions = new DexOptions();
				DexFile dexFile = new DexFile(dexOptions);
				DxContext dxContext = new DxContext();
				String classFilePath = String.format("%s.class", name.replace('.', '/'));
				DirectClassFile directClassFile = new DirectClassFile(code, classFilePath, true);
				directClassFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
				dexFile.add(CfTranslator.translate(dxContext, directClassFile, code, new CfOptions(), dexOptions, dexFile));
                try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                    dexFile.writeTo(byteArrayOutputStream, null, true);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    ClassLoader classLoader = new InMemoryDexClassLoader(ByteBuffer.wrap(byteArray), new FixClassloader(getClass().getClassLoader(), classManager));
                    Class<?> clazz = classLoader.loadClass(name);
                    classManager.cachedClassMap.put(name, clazz);
                    return clazz;
                }
			} catch (Exception ignored) {
			}

			return defineClass( name, code, 0, code.length );
		} else
			// Let superclass BshClassLoader (URLClassLoader) findClass try
			// to find the class...
			return super.findClass( name );
	}

	public String toString() {
		return super.toString() + "for files: "+map;
	}

	private static final class FixClassloader extends ClassLoader {
		private final BshClassManager classManager;

		public FixClassloader(ClassLoader parent, BshClassManager classManager) {
			super(parent);
			this.classManager = classManager;
		}

		@Override
		public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			Class<?> cachedClass = classManager.cachedClassMap.get(name);
			if (cachedClass != null) {
				return cachedClass;
			}
			return super.loadClass(name, resolve);
		}
	}

}
