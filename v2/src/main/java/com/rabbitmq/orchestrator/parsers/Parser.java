package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    protected Object getObj(String fieldPath, List<Map<String,Object>> sources) {
        for(Map<String, Object> source : sources) {
            Object obj = getObjAtPath(source, fieldPath, false);

            if (obj != null)
                return obj;
        }

        throw new InvalidInputException("Field path '" + fieldPath + "' does not exist in any source");
    }

    protected String getStrValue(Map<String,Object> f, String fieldPath) {
        return (String) getObjAtPath(f, fieldPath, true);
    }

    protected String getOptionalStrValue(Map<String,Object> f, String fieldPath, String defaultValue) {
        Object obj = getObjAtPath(f, fieldPath, false);
        if(obj == null)
            return defaultValue;

        return (String)obj;
    }

    protected String getValidatedValue(Map<String,Object> f, String fieldPath, String... permitted) {
        String value = getStrValue(f, fieldPath);
        if(isValid(permitted, value))
            return value;

        throw new InvalidInputException("'" + fieldPath + "' can only have values: " + String.join(",", permitted));
    }

    protected String getOptionalValidatedValue(Map<String,Object> f, String fieldPath, String defaultValue, String... permitted) {
        Object value = getObjAtPath(f, fieldPath, false);
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

    protected Integer getIntValue(Map<String,Object> f, String fieldPath) {
        return (Integer) getObjAtPath(f, fieldPath, true);
    }

    protected Integer getOptionalIntValue(Map<String,Object> f, String fieldPath, Integer defaultValue) {
        Object value = getObjAtPath(f, fieldPath, false);

        if(value == null)
            return defaultValue;

        return (Integer)value;
    }

    protected Boolean getBoolValue(Map<String,Object> f, String fieldPath) {
        return (Boolean) getObjAtPath(f, fieldPath, true);
    }

    protected Boolean getOptionalBoolValue(Map<String,Object> f, String fieldPath, Boolean defaultValue) {
        Object value = getObjAtPath(f, fieldPath, false);

        if(value == null)
            return defaultValue;

        return (Boolean) value;
    }

    protected  List<String> getListValue(Map<String,Object> f, String fieldPath) {
        String value = getStrValue(f, fieldPath);
        return Arrays.stream(f.get(value).toString().split(",")).collect(Collectors.toList());
    }

    protected Map<String,Object> getSubTree(Map<String,Object> f, String fieldPath) {
        return (Map<String,Object>)getObjAtPath(f, fieldPath, true);
    }

    protected Map<String,Object> getOptionalSubTree(Map<String,Object> f, String fieldPath, Map<String, Object> defaultValue>) {
        Object subTree = getObjAtPath(f, fieldPath, false);
        if(subTree == null)
            return defaultValue;

        return (Map<String,Object>)subTree;
    }

    protected  List<Map<String,Object>> getSubTreeList(Map<String,Object> f, String fieldPath) {
        return (List<Map<String,Object>>)getObjAtPath(f, fieldPath, true);
    }

    protected List<String> getStringList(Map<String,Object> f, String fieldPath) {
        return (List<String>) getObjAtPath(f, fieldPath, true);
    }

    protected Object getObjAtPath(Map<String,Object> f, String fieldPath, boolean isMandatory) {
        Map<String, Object> currObject = f;
        String[] fieldList = fieldPath.split("\\.");
        for(int i=0; i<fieldList.length; i++) {
            String field = fieldList[i];

            if(i == fieldList.length-1) {
                return currObject.get(field);
            }
            else if(currObject.containsKey(field))
                currObject = (Map<String, Object>)currObject.get(field);
            else
                break;
        }

        if(isMandatory)
            throw new InvalidInputException("Path '" + fieldPath + "' does not exist");

        return null;
    }

    protected Boolean pathExists(Map<String,Object> f, String fieldPath) {
        Map<String, Object> currObject = f;
        String[] fieldList = fieldPath.split("\\.");
        for(int i=0; i<fieldList.length; i++) {
            String field = fieldList[i];

            if(i == fieldList.length-1) {
                return true;
            }
            else if(currObject.containsKey(field))
                currObject = (Map<String, Object>)currObject.get(field);
            else
                break;
        }

        return false;
    }
}
