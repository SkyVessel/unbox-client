package dev.unbox.client;

/** Reflection used by test fixtures in both named development and intermediary production runs. */
final class PortReflection {
    static java.lang.reflect.Field field(Class<?> type,String named,String intermediary)throws NoSuchFieldException{
        try{return type.getDeclaredField(named);}catch(NoSuchFieldException ignored){return type.getDeclaredField(intermediary);}
    }
    static java.lang.reflect.Method method(Class<?> type,String named,String intermediary,Class<?>... args)throws NoSuchMethodException{
        try{return type.getDeclaredMethod(named,args);}catch(NoSuchMethodException ignored){return type.getDeclaredMethod(intermediary,args);}
    }
}
