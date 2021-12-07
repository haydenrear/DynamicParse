import java.io.Serializable;

public class media implements Serializable {
  public String next_page;
  
  public Long per_page;
  
  public Long page;
  
  public Long total_results;
  
  public static class photos implements Serializable {
    public Long width;
    
    public String avg_color;
    
    public String photographer;
    
    public String photographer_url;
    
    public Long id;
    
    public String url;
    
    public Long photographer_id;
    
    public Boolean liked;
    
    public Long height;
    
    public static class src implements Serializable {
      public String small;
      
      public String original;
      
      public String large;
      
      public String tiny;
      
      public String medium;
      
      public String large2x;
      
      public String portrait;
      
      public String landscape;
    }
  }
}
