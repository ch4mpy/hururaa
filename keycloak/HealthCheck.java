public class HealthCheck {
    public static void main(String[] args) throws java.lang.Throwable {
        java.net.URI uri = java.net.URI.create(args[0]);
        System.exit(java.net.HttpURLConnection.HTTP_OK == ((java.net.HttpURLConnection) uri.toURL().openConnection())
                .getResponseCode() ? 0 : 1);
    }
}
