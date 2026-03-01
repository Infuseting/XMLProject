package fr.infuseting;

/**
 * Point d'entrée principal de l'application.
 * Orchestrateur du processus métier. Le flux d'exécution est strictement défini
 * :
 * 1. Initialisation (Remise à zéro de la base)
 * 2. Importation du catalogue (et calcul des prix de vente)
 * 3. Traitement de la commande client (avec vérification de cohérence des
 * stocks)
 * 4. Export comptable / logistique (Génération du XML récapitulatif)
 */
public class App {
    public static final String db_url = "jdbc:mysql://localhost:3306/mini_projet";
    public static final String db_user = "root";
    public static final String db_pass = "root";

    public static void resetDatabase() {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(db_url, db_user, db_pass);
                java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0;");
            stmt.executeUpdate("DROP TABLE IF EXISTS Lignes_Commande;");
            stmt.executeUpdate("DROP TABLE IF EXISTS Commandes;");
            stmt.executeUpdate("DROP TABLE IF EXISTS Produits;");
            stmt.executeUpdate("DROP TABLE IF EXISTS Clients;");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1;");
            java.nio.file.Path path = java.nio.file.Paths.get("init.sql");
            String sql = java.nio.file.Files.readString(path);
            for (String query : sql.split(";")) {
                query = query.trim();
                if (!query.isEmpty()) {
                    stmt.execute(query);
                }
            }
            System.out.println("Base de données réinitialisée avec succès.");
        } catch (Exception e) {
            System.err.println("Erreur lors de la réinitialisation de la base : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== DÉBUT DU MINI-PROJET XML / JDBC ===");
        resetDatabase();
        try {
            System.out.println("\n--- Partie 1 : Importation des Produits ---");
            GestionProduits gestionProduits = new GestionProduits();
            gestionProduits.importerProduits();
            System.out.println("\n--- Partie 2 : Traitement de la Commande ---");
            GestionCommandes gestionCommandes = new GestionCommandes();
            gestionCommandes.traiterCommande();
            System.out.println("\n--- Partie 3 : Exportation des Commandes ---");
            ExportXML exportXML = new ExportXML();
            exportXML.genererExport();

            System.out.println("\n=== FIN DE L'EXÉCUTION AVEC SUCCÈS ===");

        } catch (Exception e) {
            System.err.println("\nUne erreur globale est survenue : " + e.getMessage());
            e.printStackTrace();
        }
    }
}