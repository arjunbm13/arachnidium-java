package com.github.arachnidium.model.common;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.MethodProxy;

import org.objectweb.asm.Type;

abstract class MethodReadingUtil {
	
	private static final HashMap<Class<?>, Class<?>> FOR_USED_SIMPLE_TYPES = new HashMap<Class<?>, Class<?>>() {
		private static final long serialVersionUID = 1L;
		{
			put(Integer.class, int.class);
			put(Long.class, long.class);
			put(Boolean.class, boolean.class);
			// other can be added
		}
	};

	private MethodReadingUtil(){
		super();
	}

	static Class<?>[] getParameterClasses(Object[] paramerers,
			Class<?> requiredClass) {
	
		Class<?>[] givenParameters = new Class<?>[paramerers.length];
		for (int i = 0; i < paramerers.length; i++) {
			givenParameters[i] = paramerers[i].getClass();
		}	
		Constructor<?>[] declaredConstructors = requiredClass
				.getDeclaredConstructors();
		Class<?>[] result = getSuitableParameterClasses(declaredConstructors, paramerers);
		if (result != null){
			return result;
		}
		throw new RuntimeException(new NoSuchMethodException(
				"There is no suitable constructor! Given parameters: "
						+ Arrays.asList(givenParameters).toString() + ". "
						+ "Class is " + requiredClass.getName()));
	}

	private static Class<?>[] getSuitableParameterClasses(
			Executable[] executables, Object[] paramerers) {
	
		Class<?>[] givenParameters = new Class<?>[paramerers.length];
		for (int i = 0; i < paramerers.length; i++) {
			givenParameters[i] = paramerers[i].getClass();
		}
	
		for (Executable executable : executables) {
			Class<?>[] declaredParameters = executable.getParameterTypes();
	
			if (declaredParameters.length != givenParameters.length) {
				continue;
			}
	
			boolean isMatch = true;
			for (int i = 0; i < declaredParameters.length; i++) {
				boolean areParametersMatch = declaredParameters[i]
						.isAssignableFrom(givenParameters[i]);
				Class<?> simpleType = FOR_USED_SIMPLE_TYPES
						.get(givenParameters[i]);
				boolean isCastedToSimple = (simpleType != null);
				if (!areParametersMatch && isCastedToSimple) {
					areParametersMatch = declaredParameters[i]
							.isAssignableFrom(simpleType);
				}
				if (!areParametersMatch) {
					isMatch = false;
					break;
				}
			}
	
			if (isMatch) {
				return declaredParameters;
			}
		}
		return null;
	}

	static MethodProxy getMethodProxy(Class<?> clazz, Method m){
		Type returned = Type.getReturnType(m);
		Type[] argTypes = Type.getArgumentTypes(m);
		Signature s = new Signature(m.getName(), returned, argTypes);
		return MethodProxy.find(clazz, s);		
	}

	static int getParameterIndex(Parameter[] parameters, Class<?> requredClass){
		for (int i = 0; i < parameters.length; i ++){
			if (parameters[i].getType().isAssignableFrom(requredClass)){
				return i;
			}
		}
		return -1;
	}

	@SuppressWarnings("unchecked")
	static <T> T getDefinedParameter(Method method, Class<?> desiredClass, Object[] args){
		int paramIndex = getParameterIndex(
				method.getParameters(), desiredClass);
		if (paramIndex >= 0){
			return (T) args[paramIndex];
		}
		return null;
	}

	static Method getSuitableMethod(Class<?> clazz, String methodName, Object[] argValues){
		Method[] declaredMethods = clazz.getMethods();
		List<Method> found = new ArrayList<Method>();
		for (Method m: declaredMethods){
			if (!m.getName().equals(methodName)){
				continue;
			}
			found.add(m);
		}
		
		if (found.size() == 0){
			return null;
		}
		
		for (Method m: found){
			Method[] methods = new Method[] {m};
			Class<?>[] result = getSuitableParameterClasses(methods, argValues);
			if (result == null){
				continue;
			}
			return m;
		}
		return null;
	}
	
	
}
