public class DebugNormalization {
    public static void main(String[] args) {
        String originalText = "\"Shoo! Woh kan i! Woh kan i!\"\n\nXiù giggled as the little alien kid ran over";
        
        // Current normalization logic
        String normalized = originalText.trim()
                   .replaceAll("\\r\\n", "\n")          // Normalize line endings 
                   .replaceAll("\\r", "\n")             // Handle old Mac line endings
                   .replaceAll("\\n{3,}", "\n\n")       // Collapse 3+ newlines to 2 (preserve paragraphs)
                   .replaceAll("[ \\t]+", " ")          // Collapse spaces/tabs only (preserve newlines)
                   .replaceAll("\\n ", "\n")            // Remove spaces after newlines  
                   .replaceAll(" \\n", "\n");           // Remove spaces before newlines
        
        System.out.println("ORIGINAL:");
        System.out.println(originalText);
        System.out.println("\nNORMALIZED:");
        System.out.println(normalized);
        System.out.println("\nAs bytes:");
        for (byte b : normalized.getBytes()) {
            System.out.print((char)b + "(" + (int)b + ") ");
        }
        System.out.println();
    }
}
