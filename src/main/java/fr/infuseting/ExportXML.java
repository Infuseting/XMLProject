package fr.infuseting;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.FileWriter;
import java.sql.*;

/**
 * Gère l'extraction et la mise en forme des commandes sous format XML.
 * Correspond à l'étape finale du processus métier (reporting, comptabilité, ou
 * transmission vers un ERP/logiciel tiers).
 */
public class ExportXML {

    private static final String DB_URL = App.db_url;
    private static final String DB_USER = App.db_user;
    private static final String DB_PASS = App.db_pass;

    /**
     * Génère le fichier "export_commandes.xml" récapitulant les ventes.
     * Logique applicative : Agréger des données tabulaires et relationnelles
     * (Clients, Commandes, Lignes_Commande, Produits) en une structure hiérarchique
     * arborescente (XML).
     */
    public void genererExport() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            Element rootElement = new Element("commandes");
            Document doc = new Document(rootElement);

            String queryCommandes = "SELECT c.id, c.date_commande, c.total, cl.nom, cl.email, cl.ville " +
                    "FROM Commandes c JOIN Clients cl ON c.id_client = cl.id";

            try (Statement stmtCmd = conn.createStatement();
                    ResultSet rsCmd = stmtCmd.executeQuery(queryCommandes)) {

                while (rsCmd.next()) {
                    int idCommande = rsCmd.getInt("id");

                    int nbProduits = compterProduits(conn, idCommande);

                    Element commandeElement = new Element("commande");
                    commandeElement.setAttribute("id", "C" + idCommande);
                    commandeElement.setAttribute("nb-produit", String.valueOf(nbProduits));

                    commandeElement.addContent(new Element("nom-client").setText(rsCmd.getString("nom")));
                    commandeElement.addContent(new Element("email").setText(rsCmd.getString("email")));
                    commandeElement.addContent(new Element("ville").setText(rsCmd.getString("ville")));
                    commandeElement.addContent(new Element("date").setText(rsCmd.getString("date_commande")));
                    commandeElement.addContent(new Element("total").setText(String.valueOf(rsCmd.getDouble("total"))));

                    Element produitsElement = new Element("produits");

                    String queryLignes = "SELECT p.nom, lc.prix_unitaire, lc.quantite " +
                            "FROM Lignes_Commande lc JOIN Produits p ON lc.id_produit = p.id " +
                            "WHERE lc.id_commande = ?";

                    try (PreparedStatement stmtLignes = conn.prepareStatement(queryLignes)) {
                        stmtLignes.setInt(1, idCommande);
                        ResultSet rsLignes = stmtLignes.executeQuery();

                        while (rsLignes.next()) {
                            Element produitElement = new Element("produit");
                            produitElement.addContent(new Element("nom").setText(rsLignes.getString("nom")));
                            produitElement.addContent(
                                    new Element("prix").setText(String.valueOf(rsLignes.getDouble("prix_unitaire"))));
                            produitElement.addContent(
                                    new Element("quantité").setText(String.valueOf(rsLignes.getInt("quantite"))));
                            produitsElement.addContent(produitElement);
                        }
                    }

                    commandeElement.addContent(produitsElement);
                    rootElement.addContent(commandeElement);
                }
            }

            XMLOutputter xmlOutput = new XMLOutputter();
            xmlOutput.setFormat(Format.getPrettyFormat());
            xmlOutput.output(doc, new FileWriter("export_commandes.xml"));

            System.out.println("Le fichier export_commandes.xml a été généré avec succès !");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int compterProduits(Connection conn, int idCommande) throws SQLException {
        String query = "SELECT COUNT(*) FROM Lignes_Commande WHERE id_commande = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, idCommande);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                return rs.getInt(1);
        }
        return 0;
    }
}