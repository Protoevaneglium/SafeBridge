package com.stange.safebridge;
import java.math.BigDecimal;
import java.math.MathContext;
/** Small immediate-execution calculator; no eval, networking or persistence. */
public final class Calculator {
    private String display="0", operator="";
    private BigDecimal left;
    private boolean fresh=true;
    public String display(){return display;}
    public void reset(){display="0";operator="";left=null;fresh=true;}
    public void key(String k){
        if(k.equals("AC")){reset();return;}
        if(k.matches("[0-9.]")){
            if(fresh){display="0";fresh=false;}
            if(k.equals(".")){if(!display.contains("."))display+=".";}
            else if(display.equals("0"))display=k;else if(display.length()<18)display+=k;
            return;
        }
        if(display.equals("Error")){reset();return;}
        if(k.equals("⌫")){if(!fresh)display=display.length()>1?display.substring(0,display.length()-1):"0";if(display.equals("-"))display="0";return;}
        if(k.equals("±")){if(!display.equals("0"))display=display.startsWith("-")?display.substring(1):"-"+display;return;}
        if(k.equals("%")){display=format(new BigDecimal(display).divide(BigDecimal.valueOf(100)));return;}
        if(!k.equals("=")&&!k.equals("+")&&!k.equals("−")&&!k.equals("×")&&!k.equals("÷"))return;
        try{
            BigDecimal right=new BigDecimal(display);
            if(left!=null&&!operator.isEmpty()&&!fresh){
                switch(operator){case "+":right=left.add(right);break;case "−":right=left.subtract(right);break;case "×":right=left.multiply(right);break;case "÷":right=left.divide(right,MathContext.DECIMAL64);break;}
                display=format(right);
            }
            left=k.equals("=")?null:new BigDecimal(display);operator=k.equals("=")?"":k;fresh=true;
        }catch(ArithmeticException|NumberFormatException e){display="Error";left=null;operator="";fresh=true;}
    }
    private String format(BigDecimal n){return n.round(MathContext.DECIMAL64).stripTrailingZeros().toPlainString();}
}
