package com.wtm.util;

import java.util.*;

/**
 * Small dependency-free JSON parser used by the application's provider
 * adapters. Keeping it local allows the production JAR to remain self-contained.
 *
 * The parser is intentionally defensive: malformed input is rejected, trailing
 * garbage is not accepted, and nesting depth is capped so an unexpected remote
 * response cannot exhaust the Java call stack.
 */
public final class MiniJson {
    private static final int MAX_NESTING_DEPTH=64;

    private MiniJson(){}

    public static Object parse(String json){
        if(json==null)
            throw new IllegalArgumentException("JSON input is null.");

        Parser parser=new Parser(json);
        Object value=parser.parseValue(0);
        parser.skipWhitespace();

        if(!parser.atEnd())
            throw parser.error("Unexpected trailing content");

        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> obj(Object value){
        return (Map<String,Object>)value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object value){
        return (List<Object>)value;
    }

    public static String str(Object value){
        return value==null?"":String.valueOf(value);
    }

    public static double num(Object value){
        return value instanceof Number number
                ?number.doubleValue()
                :Double.parseDouble(String.valueOf(value));
    }

    public static int integer(Object value){
        return (int)Math.round(num(value));
    }

    private static final class Parser {
        private final String source;
        private int index;

        Parser(String source){
            this.source=source;
        }

        Object parseValue(int depth){
            if(depth>MAX_NESTING_DEPTH)
                throw error("JSON nesting exceeds safety limit");

            skipWhitespace();
            if(atEnd())throw error("Unexpected end of JSON");

            char current=source.charAt(index);

            return switch(current){
                case '{' -> object(depth+1);
                case '[' -> array(depth+1);
                case '"' -> string();
                case 't' -> literal("true",Boolean.TRUE);
                case 'f' -> literal("false",Boolean.FALSE);
                case 'n' -> literal("null",null);
                default -> number();
            };
        }

        private Map<String,Object> object(int depth){
            expect('{');
            skipWhitespace();

            Map<String,Object> map=new LinkedHashMap<>();

            if(peek('}')){
                index++;
                return map;
            }

            while(true){
                skipWhitespace();
                if(!peek('"'))
                    throw error("Object key must be a string");

                String key=string();
                skipWhitespace();
                expect(':');

                map.put(key,parseValue(depth));
                skipWhitespace();

                if(peek('}')){
                    index++;
                    return map;
                }

                expect(',');
            }
        }

        private List<Object> array(int depth){
            expect('[');
            skipWhitespace();

            List<Object> values=new ArrayList<>();

            if(peek(']')){
                index++;
                return values;
            }

            while(true){
                values.add(parseValue(depth));
                skipWhitespace();

                if(peek(']')){
                    index++;
                    return values;
                }

                expect(',');
            }
        }

        private String string(){
            expect('"');
            StringBuilder result=new StringBuilder();

            while(!atEnd()){
                char current=source.charAt(index++);

                if(current=='"')
                    return result.toString();

                if(current=='\\'){
                    if(atEnd())
                        throw error("Unterminated escape sequence");

                    char escaped=source.charAt(index++);

                    switch(escaped){
                        case '"','\\','/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(readUnicodeEscape());
                        default -> throw error("Invalid string escape");
                    }
                }else{
                    if(current<0x20)
                        throw error("Control character in JSON string");

                    result.append(current);
                }
            }

            throw error("Unterminated JSON string");
        }

        private char readUnicodeEscape(){
            if(index+4>source.length())
                throw error("Incomplete Unicode escape");

            String hex=source.substring(index,index+4);
            index+=4;

            try{
                return (char)Integer.parseInt(hex,16);
            }catch(NumberFormatException ex){
                throw error("Invalid Unicode escape");
            }
        }

        private Object number(){
            int start=index;

            if(peek('-'))index++;

            if(atEnd())throw error("Invalid JSON number");

            if(peek('0')){
                index++;
            }else{
                if(!isDigit(current()))
                    throw error("Invalid JSON value");

                while(!atEnd()&&isDigit(current()))index++;
            }

            if(!atEnd()&&peek('.')){
                index++;
                if(atEnd()||!isDigit(current()))
                    throw error("Invalid fractional number");

                while(!atEnd()&&isDigit(current()))index++;
            }

            if(!atEnd()&&(peek('e')||peek('E'))){
                index++;
                if(!atEnd()&&(peek('+')||peek('-')))index++;

                if(atEnd()||!isDigit(current()))
                    throw error("Invalid exponent");

                while(!atEnd()&&isDigit(current()))index++;
            }

            String token=source.substring(start,index);

            try{
                if(token.indexOf('.')>=0
                        ||token.indexOf('e')>=0
                        ||token.indexOf('E')>=0){
                    double value=Double.parseDouble(token);
                    if(!Double.isFinite(value))
                        throw error("Non-finite JSON number");
                    return value;
                }

                return Long.parseLong(token);
            }catch(NumberFormatException ex){
                throw error("Invalid JSON number");
            }
        }

        private Object literal(String text,Object value){
            if(!source.startsWith(text,index))
                throw error("Invalid JSON literal");

            index+=text.length();
            return value;
        }

        void skipWhitespace(){
            while(!atEnd()&&Character.isWhitespace(current()))index++;
        }

        boolean atEnd(){
            return index>=source.length();
        }

        private char current(){
            return source.charAt(index);
        }

        private boolean peek(char value){
            return !atEnd()&&current()==value;
        }

        private void expect(char value){
            skipWhitespace();

            if(!peek(value))
                throw error("Expected '"+value+"'");

            index++;
        }

        private static boolean isDigit(char value){
            return value>='0'&&value<='9';
        }

        IllegalArgumentException error(String message){
            return new IllegalArgumentException(
                    message+" at JSON offset "+index+"."
            );
        }
    }
}
