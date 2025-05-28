package model;

public class Campain {
    private String uuid;
    private String name;
    private String neo4jLabel;
    private String quadrantCollectionName;
    
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getNeo4jLabel() {
        return neo4jLabel;
    }
    
    public void setNeo4jLabel(String neo4jLabel) {
        this.neo4jLabel = neo4jLabel;
    }
    
    public String getQuadrantCollectionName() {
        return quadrantCollectionName;
    }
    
    public void setQuadrantCollectionName(String quadrantCollectionName) {
        this.quadrantCollectionName = quadrantCollectionName;
    }
    
    public Campain() {}
    
    public Campain(String uuid, String name, String neo4jLabel, String quadrantCollectionName) {
        this.uuid = uuid;
        this.name = name;
        this.neo4jLabel = neo4jLabel;
        this.quadrantCollectionName = quadrantCollectionName;
    }
    
    @Override
    public String toString() {
        return "[" + uuid + "] - " + name + " - " + neo4jLabel + " - " + quadrantCollectionName;
    }
}
