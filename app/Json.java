import java.math.BigDecimal;
import java.util.*;

/** Small strict JSON reader for provider responses; no evaluation or dependencies. */
final class Json {
    private final String input;
    private int at;
    private Json(String input) { this.input=input; }
    static Object parse(String input) {
        Json p=new Json(input); Object value=p.value(0); p.space();
        if(p.at!=input.length()) throw new IllegalArgumentException("JSON có dữ liệu dư.");
        return value;
    }
    private void space() { while(at<input.length() && " \r\n\t".indexOf(input.charAt(at))>=0) at++; }
    private Object value(int depth) {
        if(depth>64) throw new IllegalArgumentException("JSON quá sâu.");
        space(); if(at>=input.length()) throw new IllegalArgumentException("JSON thiếu giá trị.");
        char c=input.charAt(at);
        if(c=='"') return string();
        if(c=='{' || c=='[') {
            at++; space();
            if(c=='{') {
                Map<String,Object> out=new LinkedHashMap<>();
                if(take('}')) return out;
                do {
                    space(); String key=string(); space(); need(':');
                    if(out.containsKey(key)) throw new IllegalArgumentException("JSON trùng khóa.");
                    out.put(key,value(depth+1)); space();
                    if(take('}')) return out;
                    need(',');
                } while(true);
            }
            List<Object> out=new ArrayList<>();
            if(take(']')) return out;
            do { out.add(value(depth+1)); space(); if(take(']')) return out; need(','); } while(true);
        }
        for(String literal:List.of("true","false","null")) {
            if(input.startsWith(literal,at)) { at+=literal.length(); return literal.equals("null")?null:Boolean.valueOf(literal); }
        }
        int start=at;
        while(at<input.length() && "-+0123456789.eE".indexOf(input.charAt(at))>=0) at++;
        String number=input.substring(start,at);
        if(!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("JSON sai số.");
        return new BigDecimal(number);
    }
    private boolean take(char c) { if(at<input.length() && input.charAt(at)==c) { at++; return true; } return false; }
    private void need(char c) { if(!take(c)) throw new IllegalArgumentException("JSON thiếu ký tự phân cách."); }
    private String string() {
        need('"'); StringBuilder out=new StringBuilder();
        while(at<input.length()) {
            char c=input.charAt(at++);
            if(c=='"') return out.toString();
            if(c<32) throw new IllegalArgumentException("JSON sai chuỗi.");
            if(c!='\\') { out.append(c); continue; }
            if(at>=input.length()) break;
            c=input.charAt(at++);
            switch(c) {
                case '"','\\','/' -> out.append(c);
                case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                case 'u' -> {
                    if(at+4>input.length()) throw new IllegalArgumentException("JSON thiếu Unicode.");
                    out.append((char)Integer.parseInt(input.substring(at,at+4),16)); at+=4;
                }
                default -> throw new IllegalArgumentException("JSON sai escape.");
            }
        }
        throw new IllegalArgumentException("JSON thiếu dấu kết thúc chuỗi.");
    }
}
