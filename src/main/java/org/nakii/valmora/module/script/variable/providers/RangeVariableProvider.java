package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RangeVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "range";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        // We need at least range.<start>.<end>
        if (path.length < 2) return null;

        int start;
        int end;

        // 1. Resolve Start (Assuming the first parameter is a hardcoded number like 1)
        try {
            start = Integer.parseInt(path[0]);
        } catch (NumberFormatException e) {
            return null;
        }

        // 2. Resolve End (Dynamically!)
        // We join the rest of the path back together. 
        // Example:["prop", "target_skill", "max_level"] -> "prop.target_skill.max_level"
        String endPath = String.join(".", Arrays.copyOfRange(path, 1, path.length));
        
        try {
            // First, try to parse it as a raw number in case they used $range.1.60$
            end = Integer.parseInt(endPath);
        } catch (NumberFormatException e) {
            // It's a variable path! Let the engine's VariableResolver fetch it.
            Object resolved = ValmoraAPI.getInstance().getScriptModule()
                                        .getVariableResolver().resolve(endPath, context);
            
            if (resolved instanceof Number num) {
                end = num.intValue();
            } else if (resolved instanceof String str) {
                try { 
                    end = Integer.parseInt(str); 
                } catch (NumberFormatException ex) { 
                    return null; 
                }
            } else {
                return null; // Could not resolve to a valid number
            }
        }

        // 3. Generate and return the List
        List<Integer> list = new ArrayList<>();
        if (start <= end) {
            for (int i = start; i <= end; i++) list.add(i);
        } else {
            // Supports reverse ranges like range.60.1 just in case!
            for (int i = start; i >= end; i--) list.add(i);
        }
        return list;
    }
}
