package com.spl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BasicCodeGenerator {
    
    private static final int LINE_NUMBER_STEP = 10;
    private static final int STARTING_LINE_NUMBER = 10;
    
    private String intermediateCode;
    private Map<String, Integer> labelToLineNumber;
    private List<String> numberedLines;
    
    public BasicCodeGenerator(String intermediateCode) {
        this.intermediateCode = intermediateCode;
        this.labelToLineNumber = new HashMap<>();
        this.numberedLines = new ArrayList<>();
    }
    

    public String generate() {
        if (intermediateCode == null || intermediateCode.trim().isEmpty()) {
            return "";
        }
        
        // Step 1: Split and assign line numbers
        String[] lines = intermediateCode.split("\n");
        assignLineNumbers(lines);
        
        // Step 2: Replace label references with line numbers
        replaceLabelReferences();
        
        // Step 3: Build final BASIC code
        return buildBasicCode();
    }
    private void assignLineNumbers(String[] lines) {
        int currentLineNumber = STARTING_LINE_NUMBER;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            if (trimmedLine.isEmpty()) {
                continue;
            }
            
            // Check if this line is a label (REM Lx)
            Pattern labelPattern = Pattern.compile("^REM\\s+(L\\d+)\\s*$");
            Matcher labelMatcher = labelPattern.matcher(trimmedLine);
            
            if (labelMatcher.matches()) {
                String label = labelMatcher.group(1);
                labelToLineNumber.put(label, currentLineNumber);
            }
            numberedLines.add(currentLineNumber + " " + trimmedLine);
            currentLineNumber += LINE_NUMBER_STEP;
        }
    }
    
    
    //Replace GOTO Lx and THEN Lx with actual line numbers
    private void replaceLabelReferences() {
        for (int i = 0; i < numberedLines.size(); i++) {
            String line = numberedLines.get(i);
            
            // Replace GOTO Lx with GOTO lineNumber
            line = replaceGotoLabels(line);
            
            // Replace THEN Lx with THEN lineNumber
            line = replaceThenLabels(line);
            
            numberedLines.set(i, line);
        }
    }
    
    
    //Replace GOTO Lx with GOTO lineNumber
    private String replaceGotoLabels(String line) {
        Pattern gotoPattern = Pattern.compile("GOTO\\s+(L\\d+)");
        Matcher gotoMatcher = gotoPattern.matcher(line);
        
        StringBuffer result = new StringBuffer();
        while (gotoMatcher.find()) {
            String label = gotoMatcher.group(1);
            Integer lineNumber = labelToLineNumber.get(label);
            
            if (lineNumber != null) {
                gotoMatcher.appendReplacement(result, "GOTO " + lineNumber);
            } else {
                // Keep original if label not found (shouldn't happen)
                gotoMatcher.appendReplacement(result, gotoMatcher.group(0));
            }
        }
        gotoMatcher.appendTail(result);
        
        return result.toString();
    }

    //Replace THEN Lx with THEN lineNumber
    private String replaceThenLabels(String line) {
        Pattern thenPattern = Pattern.compile("THEN\\s+(L\\d+)");
        Matcher thenMatcher = thenPattern.matcher(line);
        
        StringBuffer result = new StringBuffer();
        while (thenMatcher.find()) {
            String label = thenMatcher.group(1);
            Integer lineNumber = labelToLineNumber.get(label);
            
            if (lineNumber != null) {
                thenMatcher.appendReplacement(result, "THEN " + lineNumber);
            } else {
                // Keep original if label not found (shouldn't happen)
                thenMatcher.appendReplacement(result, thenMatcher.group(0));
            }
        }
        thenMatcher.appendTail(result);
        
        return result.toString();
    }
    
    //Build the final BASIC code string
    private String buildBasicCode() {
        StringBuilder basicCode = new StringBuilder();
        
        for (String line : numberedLines) {
            basicCode.append(line).append("\n");
        }
        
        return basicCode.toString();
    }
    
    
    public Map<String, Integer> getLabelMapping() {
        return new HashMap<>(labelToLineNumber);
    }
    
    public Integer getLineNumberForLabel(String label) {
        return labelToLineNumber.get(label);
    }
}