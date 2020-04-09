import java.util.ArrayList;

class Test 
{ 
    // Test code/algorithm for recursively splitting items into separate buckets with a given maximum size 
    public static void main(String[] args) 
    { 
        ArrayList<String> list = new ArrayList<String>();
        list.add("sakd2");
        list.add("dakdmsaiodaq");
        list.add("sakdm2io211");
        list.add("sakdasd");
        list.add("skaldamkda");
        list.add("skds");
        list.add("sdkada");
        list.add("siodqwiowd1");
        list.add("sakdamdk2");
        split(list);
    }
    
    public static void split(String data){
        if(data.length() > 0){
            String overflow = "";
            int size = 20;
            int idxFrom = 0;
            while(idxFrom < size){
                idxFrom = data.indexOf('~', idxFrom);
            }
            data = data.substring(0, idxFrom);
            overflow = data.substring(idxFrom);
            System.out.println(data);
            //split(overflow);
        }
    }

    public static void split(ArrayList<String> list){
        if(list.size() == 0){ return; } // base case for recursion
        String data = "";
        int size = 20;
        int idx;
        for(idx = 0; idx < list.size(); idx++){
            if(data.length() + list.get(idx).length() < size){
                data += list.get(idx) + "~";
            } else { break; }
        }
        System.out.println(data);
        ArrayList<String> temp = new ArrayList<String>(list.subList(idx, list.size()));
        split(temp); // recursively call on the subList
        System.out.println(temp);
    }
} 

