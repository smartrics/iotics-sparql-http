package smartrics.iotics.sparqlhttp;

class MediaType {
    private String type;
    private double quality;

    public MediaType(String mediaRange) {
        String[] parts = mediaRange.split(";");
        this.type = parts[0].trim();
        this.quality = 1.0; // Default quality value

        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("q=")) {
                try {
                    this.quality = Double.parseDouble(part.substring(2));
                } catch (NumberFormatException e) {
                    this.quality = 1.0; // Default quality value if parsing fails
                }
            }
        }
    }

    public String getType() {
        return type;
    }

    public double getQuality() {
        return quality;
    }

    public boolean isWildcard() {
        return "*/*".equals(type);
    }

}