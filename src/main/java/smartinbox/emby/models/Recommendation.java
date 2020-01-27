package smartinbox.emby.models;

public class Recommendation {
    private String title;
    private String id;
    private RecommendationType recommendationType;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    public Recommendation(String id, String title, RecommendationType recommendationType) {
        this.id = id;
        this.title = title;
        this.recommendationType = recommendationType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RecommendationType getRecommendationType() {
        return recommendationType;
    }

    public void setRecommendationType(RecommendationType recommendationType) {
        this.recommendationType = recommendationType;
    }
}
