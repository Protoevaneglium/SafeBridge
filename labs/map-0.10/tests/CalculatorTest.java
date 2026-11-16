import com.stange.safebridge.Calculator;
public class CalculatorTest {
 static int count;
 static void check(String expected,String...keys){Calculator c=new Calculator();for(String k:keys)c.key(k);if(!expected.equals(c.display()))throw new AssertionError(expected+" != "+c.display());count++;}
 public static void main(String[] args){
 check("5","2","+","3","=");check("-1","2","−","3","=");check("12","3","×","4","=");check("2.5","5","÷","2","=");check("Error","1","÷","0","=");check("0.3","0",".","1","+","0",".","2","=");check("0.5","5","0","%");check("-7","7","±");check("7","7","±","±");check("0","1","AC");check("12","1","2","3","⌫");check("0","1","⌫");check("1.2","1",".",".","2");check("9","1","÷","0","=","9");check("15","2","+","3","×","3","=");check("6","2","+","×","3","=");check("5","2","+","3","=","=");check("5","2","+","3","=","+","0","=");check("3","2","+","3","AC","3");check("0","1","±","⌫");check("0.3333333333333333","1","÷","3","=");
 System.out.println("PASS "+count+" calculator arithmetic/repeated/interruption/reset cases");}
}
