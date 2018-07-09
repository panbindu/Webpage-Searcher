package websearcher;

import java.io.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.*;
/**
 *
 * @author Bindu Pan
 */
public class WebSearcher {
    private final String fileUrl;
    private final String searchPattern;
    private static Semaphore fetchPemission = new Semaphore(20);
    //limit the number of threads
    private static Semaphore writePermission = new Semaphore(1);
    //controll write
        
    private static class FetcherThread extends Thread {
        private final String url;
        private final String search;
        private final BufferedWriter bw;
        private final int[] cf;
        
        FetcherThread(String url, String search, BufferedWriter bw, int[] cf) {
            this.url = url;
            this.search = search.toLowerCase();
            this.bw = bw;
            this.cf = cf;
        }
        
        private String fetchPageJsoup() {
            String res = "NO";
            try {
                String targetUrl ="http://www." + this.url;
                String html = Jsoup.connect(targetUrl).get().html();
                Pattern pattern = Pattern.compile(this.search
                        , Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(html);
                if(matcher.find()) {
                    res = "YES";
                }
            }
            catch (MalformedURLException mue) {
                res = "URLException";
                //mue.printStackTrace();
            } 
            catch (IOException ioe) {
                res = "IOException";
                //ioe.printStackTrace();
            } 
            return res;
        }
        
        @Override
        public void run() {
            try {	
                fetchPemission.acquire();
                System.out.println("Fetch Thread: starts fetching "
                        + this.url );
                System.out.println(fetchPemission.availablePermits() 
                        + " Idle Threads");
                try {
                    String foundPattern = fetchPageJsoup();
                    writePermission.acquire();
                    bw.write(this.url +", " + this.search
                            +", "+foundPattern+"\n");
                    //status statics 
                    if(foundPattern.equals("YES")) {
                        cf[1]++;
                    }
                    else if(foundPattern.equals("NO")) {
                        cf[2]++;
                    }   
                } 
                catch (IOException e){
                    e.printStackTrace();
                }
                finally {
                        cf[0]++;
                        writePermission.release();
                        // calling release() after a successful acquire()
                        fetchPemission.release();
                        System.out.println("Fetch Thread: finished fetching "
                                + this.url);
                        System.out.println(fetchPemission.availablePermits() 
                        + " Idle Threads");
                }
            } 
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public WebSearcher(String fileUrl, String searchPattern) throws Exception{
        this.fileUrl = fileUrl;
        this.searchPattern = searchPattern;
    }
    
    private void process() throws Exception {
        System.out.println("Main Thread: "
                + "Read txt file from this address: " + fileUrl);
        URL target = new URL(fileUrl);
        BufferedReader in = new BufferedReader(
        new InputStreamReader(target.openStream()));
        
        String fileName = "results.txt";
        String filePath = Paths.get(".").toAbsolutePath().normalize()
                .toString()+"/"+fileName;
        File file = new File(filePath);
        if(file.exists()) {
            new FileWriter(file.getAbsoluteFile()).close();
        }
        FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("\"URL\", \"Search Pattern\", \"Finding Status\"\n");
        
        int count = 0; // count the number of url lines readed from input
        int[] cf = new int[3]; // cf[0] to store the number of completed thread
                               // cf[1] record number of found 
                               // cf[2] record number of not found
        String inputLine;
        in.readLine();//skip first line of titles
        while ((inputLine = in.readLine()) != null) {
            String targetUrl = getUrl(inputLine);
            FetcherThread ft = new FetcherThread(targetUrl, this.searchPattern
                    , bw, cf);
            ft.start();
            count++;    
        }
        in.close();
        System.out.format("Main Thread: "
                + "Finished reading txt file, %d lines obtained\n"
                , count);
        Integer pre =0;
        while(true) {
            if(count == cf[0]) {
                //close the buffered writter only if all lines in the file
                //have been processed 
                bw.close();
                break;
            }
            if(pre != cf[0]) {
                System.out.format("Main Thread: %.2f%% (%d/%d) completed\n"
                        ,(double)cf[0]/count*100, cf[0], count);
                pre = cf[0];
            }
            Thread.sleep(800);
        }
        
        System.out.format(
                "Main Thread: Finishes processing"
                        + " %d URLs for search patten: %s\n"
                , cf[0], this.searchPattern);
        System.out.format("Main Thread: "
                + "%d webpages found search pattern\n", cf[1]);
        System.out.format("Main Thread: "
                + "%d webpages didn't find\n", cf[2]);
        System.out.format("Main Thread: "
                + "%d URLs cannot be reached\n", cf[0] - cf[1] -cf[2]);
        System.out.println("Main Thread: "
                + "Output file has been saved to: "+filePath);
    }
    
    private String getUrl(String line) throws Exception {
        String[] contents = line.split(",");
        return contents[1].replace("\"", "");
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        String fileUrl = "https://s3.amazonaws.com/fieldlens-public/urls.txt";
        String searchPattern ="https://.*com";
        WebSearcher ws = new WebSearcher(fileUrl, searchPattern);
        ws.process();
    } 
}
