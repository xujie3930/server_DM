package com.szmsd.finance.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;


public class CheckUtils {


	@SuppressWarnings("rawtypes")
	public static void notEmpty(Object obj, String message) {
		if (obj == null){
			throw new IllegalArgumentException(message + " must be specified");
		}
		if (obj instanceof String && obj.toString().trim().length()==0){
			throw new IllegalArgumentException(message + " must be specified");
		}
		if (obj.getClass().isArray() && Array.getLength(obj)==0){
			throw new IllegalArgumentException(message + " must be specified");
		}
		if (obj instanceof Collection && ((Collection)obj).isEmpty()){
			throw new IllegalArgumentException(message + " must be specified");
		}
		if (obj instanceof Map && ((Map)obj).isEmpty()){
			throw new IllegalArgumentException(message + " must be specified");
		}
	}
}
