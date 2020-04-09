import java.util.*;

class Ping 
{ 
    // Test code for determining ping delta between an item in an array and the largest item in the array
    public static void main(String[] args) 
    { 
        List<Integer> ping = Arrays.asList(200, 500, 50, 350);
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