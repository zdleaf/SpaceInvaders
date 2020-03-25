import java.util.*;

class Ping 
{ 
    // Your program begins with a call to main(). 
    // Prints "Hello, World" to the terminal window. 
    public static void main(String[] args) 
    { 
        List<Integer> ping = Arrays.asList(700, 200, 300, 400);
        List<Integer> diff = Arrays.asList(0, 0, 0, 0);

        for(int i = 0; i < ping.size(); i++){
            for(int j = i+1; j < ping.size(); j++){
                System.out.println(i + "," + j);
                Integer temp = 0;
                if(ping.get(j) > ping.get(i)){
                    temp = ping.get(j) - ping.get(i);
                    System.out.println(temp);
                    if(diff.get(i) < temp){
                        diff.set(i, temp);
                    }
                    
                }
                else if(ping.get(i) > ping.get(j)){
                    temp = ping.get(i) - ping.get(j);
                    System.out.println(temp);
                    if(diff.get(j) < temp){
                        diff.set(j, temp);
                    }
                }
            }
        }
    System.out.println(diff);
    
    }

};