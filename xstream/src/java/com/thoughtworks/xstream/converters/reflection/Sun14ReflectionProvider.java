/*
 * Copyright (C) 2004, 2005 Joe Walnes.
 * Copyright (C) 2006, 2007, 2008, 2011 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 07. March 2004 by Joe Walnes
 */
package com.thoughtworks.xstream.converters.reflection;

import sun.misc.Unsafe;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Instantiates a new object on the Sun JVM by bypassing the constructor (meaning code in the constructor
 * will never be executed and parameters do not have to be known). This is the same method used by the internals of
 * standard Java serialization, but relies on internal Sun code that may not be present on all JVMs.
 *
 * @author Joe Walnes
 * @author Brian Slesinsky
 */
public class Sun14ReflectionProvider extends PureJavaReflectionProvider {

    private final static Unsafe unsafe;
    private final static Exception exception;
    // references to the Field key are kept in the FieldDictionary
    private transient Map fieldOffsetCache = new WeakHashMap();
    static {
        Unsafe u = null;
        Exception ex = null;
        try {
            Class objectStreamClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = objectStreamClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            u = (Unsafe) unsafeField.get(null);
        } catch (ClassNotFoundException e) {
            ex = e;
        } catch (SecurityException e) {
            ex = e;
        } catch (NoSuchFieldException e) {
            ex = e;
        } catch (IllegalArgumentException e) {
            ex = e;
        } catch (IllegalAccessException e) {
            ex = e;
        }
        exception = ex;
        unsafe = u;
    }

    private transient ReflectionFactory reflectionFactory = ReflectionFactory.getReflectionFactory();
    private transient Map constructorCache =new ConcurrentHashMap();

    public Sun14ReflectionProvider() {
    	super();
	}

    public Sun14ReflectionProvider(FieldDictionary dic) {
    	super(dic);
	}

    public Object newInstance(Class type) {
        try {
            Constructor customConstructor = getMungedConstructor(type);
            return customConstructor.newInstance(new Object[0]);
        } catch (NoSuchMethodException e) {
            throw new ObjectAccessException("Cannot construct " + type.getName(), e);
        } catch (SecurityException e) {
            throw new ObjectAccessException("Cannot construct " + type.getName(), e);
        } catch (InstantiationException e) {
            throw new ObjectAccessException("Cannot construct " + type.getName(), e);
        } catch (IllegalAccessException e) {
            throw new ObjectAccessException("Cannot construct " + type.getName(), e);
        } catch (IllegalArgumentException e) {
            throw new ObjectAccessException("Cannot construct " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new ObjectAccessException("Cannot construct " + type.getName(), e);
        }
    }

    private Constructor getMungedConstructor(Class type) throws NoSuchMethodException {
        synchronized (constructorCache) {
            Constructor ctor = (Constructor)constructorCache.get(type);
            if (ctor == null) {
                ctor = reflectionFactory.newConstructorForSerialization(type, Object.class.getDeclaredConstructor(new Class[0]));
                constructorCache.put(type, ctor);
            }
            return ctor;
        }
    }

    public void writeField(Object object, String fieldName, Object value, Class definedIn) {
        write(fieldDictionary.field(object.getClass(), fieldName, definedIn), object, value);
    }

    private void write(Field field, Object object, Object value) {
        if (exception != null) {
            throw new ObjectAccessException("Could not set field " + object.getClass() + "." + field.getName(), exception);
        }
        try {
            long offset = getFieldOffset(field);
            Class type = field.getType();
            if (type.isPrimitive()) {
                if (type.equals(Integer.TYPE)) {
                    unsafe.putInt(object, offset, ((Integer) value).intValue());
                } else if (type.equals(Long.TYPE)) {
                    unsafe.putLong(object, offset, ((Long) value).longValue());
                } else if (type.equals(Short.TYPE)) {
                    unsafe.putShort(object, offset, ((Short) value).shortValue());
                } else if (type.equals(Character.TYPE)) {
                    unsafe.putChar(object, offset, ((Character) value).charValue());
                } else if (type.equals(Byte.TYPE)) {
                    unsafe.putByte(object, offset, ((Byte) value).byteValue());
                } else if (type.equals(Float.TYPE)) {
                    unsafe.putFloat(object, offset, ((Float) value).floatValue());
                } else if (type.equals(Double.TYPE)) {
                    unsafe.putDouble(object, offset, ((Double) value).doubleValue());
                } else if (type.equals(Boolean.TYPE)) {
                    unsafe.putBoolean(object, offset, ((Boolean) value).booleanValue());
                } else {
                    throw new ObjectAccessException("Could not set field " +
                            object.getClass() + "." + field.getName() +
                            ": Unknown type " + type);
                }
            } else {
                unsafe.putObject(object, offset, value);
            }

        } catch (IllegalArgumentException e) {
            throw new ObjectAccessException("Could not set field " + object.getClass() + "." + field.getName(), e);
        }
    }
    
    private synchronized long  getFieldOffset(Field f)
    {
        Long l = (Long)fieldOffsetCache.get(f);
        if (l == null)
        {
            l = new Long(unsafe.objectFieldOffset(f));
            fieldOffsetCache.put(f, l);
        }
        
        return l.longValue();
    }

    protected void validateFieldAccess(Field field) {
        // (overriden) don't mind final fields.
    }

    protected Object readResolve() {
        super.readResolve();
        constructorCache = new HashMap();
        reflectionFactory = ReflectionFactory.getReflectionFactory();
        fieldOffsetCache = new WeakHashMap();
        return this;
    }
}
