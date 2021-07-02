package com.hayden.dynamicparse.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import javassist.*;
import org.apache.commons.lang3.ClassUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DynamicParseJson {

    ObjectMapper objectMapper;


    public DynamicParseJson(
            ObjectMapper objectMapper
    )
    {
        this.objectMapper = objectMapper;
    }


    /**
     * Standard class for information about classes used to make classes
     */
    public static class ClassInfo {

        CtClass clzz;
        Optional<Class<?>> arrClzz;

        public ClassInfo(
                CtClass clzz,
                Optional<Class<?>> arrClzz
        )
        {
            this.clzz = clzz;
            this.arrClzz = arrClzz;
        }

        public CtClass clzz()
        {
            return clzz;
        }

        public Optional<Class<?>> arrClzz()
        {
            return arrClzz;
        }

    }

    public Optional<ClassInfo> dynamicParse(
            String data,
            String name,
            Optional<CtClass> parentClass,
            Optional<String> directoryName
    ) throws DynamicParsingException
    {

        CtClass newClass = parentClass.orElseGet(() -> ClassPool.getDefault().makeClass(name));

        newClass.setModifiers(Modifier.PUBLIC);

        try {
            newClass.addInterface(ClassPool.getDefault().get("java.io.Serializable"));
        } catch (NotFoundException e) {
            e.printStackTrace();
        }

        try {
            var jsonParser = new JSONParser();
            Object parsed = jsonParser.parse(data);
            if (parsed instanceof JSONObject) {
                JSONObject obj = (JSONObject) parsed;
                dynamicParse(
                        obj,
                        newClass,
                        directoryName
                );
            } else if (parsed instanceof JSONArray) {
                JSONArray arr = (JSONArray) parsed;
                return Optional.of(dynamicParse(
                        arr,
                        newClass,
                        name + "List",
                        directoryName
                ));
            } else {
                throw new DynamicParsingException("Object parsed neither object nor array");
            }
            if (directoryName.isPresent()) {
                newClass.writeFile(directoryName.get());
            }
            return Optional.of(new ClassInfo(
                    newClass,
                    Optional.empty()
            ));
        } catch (ParseException | CannotCompileException | IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();

    }

    public ClassInfo dynamicParse(
            JSONArray arr,
            CtClass newClass,
            String prev,
            Optional<String> directoryName
    ) throws DynamicParsingException
    {
        if (arr.size() == 0) {
            try {
                addFieldToCtClass(
                        newClass,
                        prev,
                        ClassPool.getDefault().get(makeArray(
                                String.class,
                                1
                        ).getName())
                );
            } catch (NotFoundException e) {
                e.printStackTrace();
                System.out.println("unable to add field for value with no values");
            }
        } else {
            for (var toParse : arr) {
                if (ClassUtils.isPrimitiveOrWrapper(toParse.getClass()) || toParse instanceof String) {
                    try {
                        var ctArray = ClassPool.getDefault().get(makeArray(
                                toParse.getClass(),
                                1
                        ).getName());
                        addFieldToCtClass(
                                newClass,
                                prev,
                                ctArray
                        );
                        break;
                    } catch (NotFoundException e) {
                        e.printStackTrace();
                    }
                } else if (toParse instanceof JSONObject) {
                    JSONObject object = (JSONObject) toParse;
                    if (justAMap(arr)) {
                        try {
                            var mapArray = makeArray(
                                    Map.class,
                                    1
                            );
                            addFieldToCtClass(
                                    newClass,
                                    prev,
                                    ClassPool.getDefault().get(mapArray.getName())
                            );
                            return new ClassInfo(
                                    newClass,
                                    Optional.of(mapArray)
                            );
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                    } else {
                        var clzz = innerDynamicAndCreateCtClass(
                                object,
                                newClass,
                                directoryName.orElse(null),
                                prev,
                                classInfo -> makeArray(
                                        classInfo,
                                        1
                                )
                        );
                        return new ClassInfo(
                                newClass,
                                clzz
                        );
                    }
                } else if (toParse instanceof JSONArray) {
                    JSONArray innerArr = (JSONArray) toParse;
                    var primOrOpt = findIfPrimitive(
                            innerArr,
                            1
                    );
                    if (primOrOpt.isPresent()) {
                        var primOr = primOrOpt.get();
                        if (primOr.isPrim) {
                            try {
                                var arrCtClzz = ClassPool.getDefault().get(makeArray(
                                        primOr.primType,
                                        primOr.depth
                                ).getName());
                                addFieldToCtClass(
                                        newClass,
                                        prev,
                                        arrCtClzz
                                );
                                return new ClassInfo(
                                        newClass,
                                        Optional.of(primOr.primType())
                                );
                            } catch (NotFoundException e) {
                                e.printStackTrace();
                            }
                        } else {
                            return innerDynamicAndCreateCtClass(
                                    primOr.jo,
                                    newClass,
                                    directoryName.orElse(null),
                                    prev,
                                    classInfo -> makeArray(
                                            classInfo,
                                            primOr.depth
                                    )
                            ).flatMap(clzz -> Optional.of(new ClassInfo(
                                    newClass,
                                    Optional.of(clzz)
                            ))).orElseThrow();
                        }
                        break;
                    } else throw new DynamicParsingException("Problem recursively finding depth of array ..");
                }
                break;
            }

        }
        return null;
    }

    public void dynamicParse(
            JSONObject obj,
            CtClass newClass,
            Optional<String> directoryName
    )
            throws DynamicParsingException
    {
        for (var toParse : obj.entrySet()) {
            if (toParse instanceof Map.Entry) {
                Map.Entry entry = (Map.Entry) toParse;
                if (entry.getKey() instanceof String) {
                    String key = (String) entry.getKey();
                    if (entry.getValue() == null) {
                        try {
                            var fieldToAdd = ClassPool.getDefault().get(String.class.getName());
                            addFieldToCtClass(
                                    newClass,
                                    key,
                                    fieldToAdd
                            );
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                    } else if (ClassUtils.isPrimitiveOrWrapper(entry.getValue().getClass()) || entry.getValue() instanceof String) {
                        try {
                            var ctClass = ClassPool.getDefault().get(entry.getValue().getClass().getName());
                            addFieldToCtClass(
                                    newClass,
                                    key,
                                    ctClass
                            );
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                    } else if (entry.getValue() instanceof JSONObject) {
                        JSONObject object = (JSONObject) entry.getValue();
                        innerDynamicAndCreateCtClass(
                                object,
                                newClass,
                                directoryName.orElse(null),
                                key,
                                classInfo -> {
                                    try {
                                        return classInfo.clzz.toClass();
                                    } catch (CannotCompileException e) {
                                        e.printStackTrace();
                                    }
                                    return null;
                                }
                        );
                    } else if (entry.getValue() instanceof JSONArray) {
                        JSONArray arr = (JSONArray) entry.getValue();
                        dynamicParse(
                                arr,
                                newClass,
                                key,
                                directoryName
                        );
                    }
                }
            }
        }
    }

    private Optional<Class<?>> innerDynamicAndCreateCtClass(
            JSONObject object,
            CtClass newClass,
            String directoryName,
            String prev,
            Function<ClassInfo, Class<?>> createClzz
    ) throws DynamicParsingException
    {
        var innerDynamic = dynamicParse(
                object.toJSONString(),
                prev,
                Optional.of(newClass.makeNestedClass(
                        prev,
                        true
                )),
                Optional.ofNullable(directoryName)
        );
        return innerDynamic.map(createClzz::apply)
                .map(clzz -> {
                    try {
                        addFieldToCtClass(
                                newClass,
                                prev,
                                ClassPool.getDefault().get(clzz.getName())
                        );
                    } catch (NotFoundException e) {
                        e.printStackTrace();
                    }
                    return clzz;
                })
                .map(clzz -> {
                    try {
                        objectMapper.registerSubtypes(
                                Class.forName(innerDynamic.get().clzz.getName()),
                                clzz
                        );
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    return clzz;
                });
    }

    private Class<?> makeArray(
            ClassInfo innerDynamic,
            int depth
    )
    {
        Class<?> clzz = null;
        try {
            clzz = makeArray(
                    Class.forName(innerDynamic.clzz.getName()),
                    depth
            );
        } catch (ClassNotFoundException e) {
            try {
                clzz = makeArray(
                        innerDynamic.clzz.toClass(),
                        depth
                );
            } catch (CannotCompileException ex) {
                ex.printStackTrace();
            }
        }
        return clzz;
    }

    private Class<?> makeArray(
            Class<?> clzz,
            int depth
    )
    {
        return depth == 1
                ? Array.newInstance(
                clzz,
                1
        ).getClass()
                : Array.newInstance(
                        clzz,
                        depth,
                        1
                ).getClass();
    }

    private boolean justAMap(JSONArray arr)
    {
        Set<String> prev = null;
        for (var a : arr) {
            if (a instanceof JSONObject) {
                JSONObject obj = (JSONObject) a;
                if (obj.keySet().stream().findFirst().get() instanceof String) {
                    if (prev == null)
                        prev = obj.keySet();
                    else return !prev.containsAll((Set<String>) obj.keySet());
                }
            }
        }
        return true;
    }


    private void addFieldToCtClass(
            CtClass newClass,
            String key,
            CtClass ctClass
    )
    {
        try {
            if (newClass.isFrozen())
                newClass.defrost();
            CtField field = new CtField(
                    ctClass,
                    key,
                    newClass
            );
            field.setModifiers(Modifier.PUBLIC);
            newClass.addField(field);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }
    }

    public static final class PrimOrObj {
        private final boolean isPrim;
        private final int depth;
        private final JSONObject jo;
        private final Class<?> primType;

        public PrimOrObj(
                boolean isPrim,
                int depth,
                JSONObject jo,
                Class<?> primType
        )
        {
            this.isPrim = isPrim;
            this.depth = depth;
            this.jo = jo;
            this.primType = primType;
        }

        public boolean isPrim()
        {
            return isPrim;
        }

        public int depth()
        {
            return depth;
        }

        public JSONObject jo()
        {
            return jo;
        }

        public Class<?> primType()
        {
            return primType;
        }

    }

    public Optional<PrimOrObj> findIfPrimitive(
            JSONArray jsonArray,
            int depth
    )
    {
        for (var j : jsonArray) {
            if (ClassUtils.isPrimitiveOrWrapper(j.getClass()) || j instanceof String) {
                return Optional.of(new PrimOrObj(
                        true,
                        depth,
                        null,
                        j.getClass()
                ));
            } else if (j instanceof JSONArray) {
                JSONArray ja = (JSONArray) j;
                return findIfPrimitive(
                        ja,
                        depth + 1
                );
            } else if (j instanceof JSONObject) {
                JSONObject jo = (JSONObject) j;
                return Optional.of(new PrimOrObj(
                        false,
                        depth,
                        jo,
                        null
                ));
            }
        }
        return Optional.empty();
    }


}
