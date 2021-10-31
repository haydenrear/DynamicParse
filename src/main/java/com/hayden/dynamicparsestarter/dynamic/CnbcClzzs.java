import java.io.Serializable;

public class CnbcClzzs implements Serializable {
  public static class extensions implements Serializable {
    public static class tracing implements Serializable {
      public Long duration;
      
      public String startTime;
      
      public String endTime;
      
      public Long version;
      
      public static class execution implements Serializable {
        public resolvers[] resolvers;
        
        public static class resolvers implements Serializable {
          public Long duration;
          
          public String[] path;
          
          public String fieldName;
          
          public Long startOffset;
          
          public String parentType;
          
          public String returnType;
        }
      }
    }
  }
  
  public static class data implements Serializable {
    public static class mostPopularEntries implements Serializable {
      public assets[] assets;
      
      public String __typename;
      
      public static class assets implements Serializable {
        public String sectionHierarchyFormatted;
        
        public String authorFormatted;
        
        public String relatedTagsFilteredFormatted;
        
        public String __typename;
        
        public String description;
        
        public String type;
        
        public String pageName;
        
        public String url;
        
        public String dateFirstPublished;
        
        public String shortDateLastPublished;
        
        public String shorterHeadline;
        
        public Boolean premium;
        
        public String dateLastPublished;
        
        public Long id;
        
        public String featuredMedia;
        
        public String shortDateFirstPublished;
        
        public String headline;
        
        public static class promoImage implements Serializable {
          public String __typename;
          
          public Long id;
          
          public String url;
        }
        
        public static class section implements Serializable {
          public Boolean premium;
          
          public String shortestHeadline;
          
          public String __typename;
          
          public Long id;
          
          public String tagName;
          
          public String url;
        }
      }
    }
  }
}
