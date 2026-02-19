package fr.infuseting;

public class App {
    public static void main(String[] args) {
        System.out.println("=== DÉBUT DU MINI-PROJET XML / JDBC ===");

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