package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Parser {
    Yaml yaml;
    String rootDir;

    public Parser(Yaml yaml, String rootDir) {
        this.yaml = yaml;
        this.rootDir = rootDir;
    }

    protected Map<String,String> toStringVal(Map<String,Object> input) {
        if(input == null)
            return new HashMap<>();

        return input
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())
                ));
    }

    protected Map<String, Object> loadYamlFile(String filePath) {
        File pf = new File(filePath);
        if(!pf.exists())
            throw new InvalidInputException("File " + filePath + " provided does not exist");

        try {
            InputStream pfStream = new FileInputStream(pf);
            return yaml.load(pfStream);
        }
        catch(IOException e) {
            throw new InvalidInputException("Bad yaml file. ", e);
        }
    }

    protected Object getObj(String fieldPath, Map<String,Object>... sources) {
        for(Map<String, Object> source : sources) {
            Object obj = getObjAtPath(source, fieldPath, false);

            if (obj != null)
                return obj;
        }

        throw new InvalidInputException("Field path '" + fieldPath + "' does not exist in any source");
    }

    protected Object getOptionalObj(String fieldPath, Map<String,Object>... sources) {
        for(Map<String, Object> source : sources) {
            Object obj = getObjAtPath(source, fieldPath, false);

            if (obj != null)
                return obj;
        }

        return null;
    }

    protected String getStrValue(String fieldPath, Map<String,Object>... sources) {
        Object obj = getObj(fieldPath, sources);
        if(obj.getClass().equals(String.class))
            return (String) obj;
        else
            return String.valueOf(obj);
    }

    protected String getOptionalStrValue(String fieldPath, String defaultValue, Map<String,Object>... sources) {
        Object obj = getOptionalObj(fieldPath, sources);
        if(obj == null)
            return defaultValue;

        if(obj.getClass().equals(String.class))
            return (String) obj;
        else
            return String.valueOf(obj);
    }

    protected String getValidatedValue(String fieldPath, String[] permitted, Map<String,Object>... sources) {
        String value = getStrValue(fieldPath, sources);
        if(isValid(permitted, value))
            return value;

        throw new InvalidInputException("'" + fieldPath + "' can only have values: " + String.join(",", permitted));
    }

    protected String getOptionalValidatedValue(String fieldPath, String defaultValue, String[] permitted, Map<String,Object>... sources) {
        Object value = getOptionalObj(fieldPath, sources);
        if(value == null)
            return defaultValue;

        String strValue = (String)value;
        if(isValid(permitted, strValue))
            return strValue;

        throw new InvalidInputException("'" + fieldPath + "' can only have values: " + String.join(",", permitted));
    }

    protected boolean isValid(String[] permitted, String value) {
        boolean found = false;
        for(String v : permitted) {
            if (v.toLowerCase().equals(value.toLowerCase()))
                found = true;
        }

        return found;
    }

    protected Integer getIntValue(String fieldPath, Map<String,Object>... sources) {
        return (Integer) getObj(fieldPath, sources);
    }

    protected Integer getOptionalIntValue(String fieldPath, Integer defaultValue, Map<String,Object>... sources) {
        Object value = getOptionalObj(fieldPath, sources);

        if(value == null)
            return defaultValue;

        return (Integer)value;
    }

    protected Boolean getBoolValue(String fieldPath, Map<String,Object>... sources) {
        return (Boolean) getObj(fieldPath, sources);
    }

    protected Boolean getOptionalBoolValue(String fieldPath, Boolean defaultValue, Map<String,Object>... sources) {
        Object value = getOptionalObj(fieldPath, sources);

        if(value == null)
            return defaultValue;

        return (Boolean) value;
    }

    protected  List<String> getListValue(String fieldPath, Map<String,Object>... sources) {
        String value = getStrValue(fieldPath, sources);
        return Arrays.stream(value.split(",")).collect(Collectors.toList());
    }

    protected Map<String,Object> getSubTree(String fieldPath, Map<String,Object>... sources) {
        return (Map<String,Object>)getObj(fieldPath, sources);
    }

    protected Map<String,Object> getOptionalSubTree(String fieldPath, Map<String, Object> defaultValue, Map<String,Object>... sources) {
        Object subTree = getOptionalObj(fieldPath, sources);
        if(subTree == null)
            return defaultValue;

        return (Map<String,Object>)subTree;
    }

    protected  List<Map<String,Object>> getSubTreeList(String fieldPath, Map<String,Object>... sources) {
        return (List<Map<String,Object>>)getObj(fieldPath, sources);
    }

    protected List<String> getStringList(String fieldPath, Map<String,Object>... sources) {
        return (List<String>) getObj(fieldPath, sources);
    }

    protected List<String> getOptionalStringList(String fieldPath, List<String> defaultValue, Map<String,Object>... sources) {
        Object obj = getOptionalObj(fieldPath, sources);
        if(obj == null)
            return defaultValue;

        return (List<String>)obj;
    }

    protected Object getObjAtPath(Map<String,Object> f, String fieldPath, boolean isMandatory) {
        Map<String, Object> currObject = f;
        String[] fieldList = fieldPath.split("\\.");
        for(int i=0; i<fieldList.length; i++) {
            String field = fieldList[i];

            if(currObject.containsKey(field))
                if(i == fieldList.length-1)
                    return currObject.get(field);
                else
                    currObject = (Map<String, Object>)currObject.get(field);
            else
                break;
        }

        if(isMandatory)
            throw new InvalidInputException("Path '" + fieldPath + "' does not exist");

        return null;
    }

    protected Boolean pathExists(String fieldPath, Map<String,Object>... sources) {
        for(Map<String, Object> source : sources) {
            if (pathExists(fieldPath, source))
                return true;
        }

        return false;
    }

    protected Boolean pathExists(String fieldPath, Map<String,Object> source) {
        Map<String, Object> currObject = source;
        String[] fieldList = fieldPath.split("\\.");
        for(int i=0; i<fieldList.length; i++) {
            String field = fieldList[i];

            if(currObject.containsKey(field)) {
                if(i == fieldList.length-1)
                    return true;
                else
                    currObject = (Map<String, Object>) currObject.get(field);
            }
            else
                break;
        }

        return false;
    }

    protected List<Map<String,Object>> getSubTreesOfEachSource(String fieldPath, Map<String, Object>... sources) {
        List<Map<String,Object>> fieldList = new ArrayList<>();
        for(Map<String, Object> source : sources) {
            Object obj = getObjAtPath(source, fieldPath, false);
            if(obj != null)
                fieldList.add((Map<String, Object>)obj);
        }

        return fieldList;
    }

    protected Map<String, String> mergeStringMap(List<Map<String, Object>> sources) {
        Set<String> keys = new HashSet<>();
        for(Map<String,Object> source : sources) {
            keys.addAll(source.keySet());
        }

        Map<String, String> variables = new HashMap<>();
        for(String key : keys) {
            for(Map<String, Object> source : sources) {
                if(source.containsKey(key)) {
                    variables.put(key, convertToString(source.get(key)));
                    break;
                }
            }
        }

        return variables;
    }

    private String convertToString(Object obj) {
        if(obj.getClass().equals(String.class)) {
            return (String)obj;
        } else if(obj.getClass().equals(Integer.class) || obj.getClass().equals(Long.class)) {
            DecimalFormat df = new DecimalFormat("#");
            return df.format(obj);
        } else {
            throw new InvalidInputException("Unsupported map value type of: " + obj.getClass());
        }

    }
}
