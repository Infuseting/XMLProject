package fr.infuseting;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * Gère l'importation du catalogue de produits dans la base de données.
 * Cette classe contient la logique métier permettant d'initialiser le stock
 * et de calculer le prix de vente public en fonction du prix d'achat.
 */
public class GestionProduits {

    private static final String DB_URL = App.db_url;
    private static final String DB_USER = App.db_user;
    private static final String DB_PASS = App.db_pass;

    /**
     * Importe la liste des produits depuis un fichier XML ("produits.xml") et les
     * insère en base de données.
     * Logique métier clé appliquée lors de l'import :
     * - Le fichier XML contient le prix fournisseur (prix d'achat).
     * - Le prix de vente final est fixé selon une marge de 100% (prix de vente =
     * prix fournisseur * 2).
     */
    public void importerProduits() {
        SAXBuilder builder = new SAXBuilder();
        InputStream xmlStream = GestionProduits.class.getClassLoader().getResourceAsStream("produits.xml");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            Document document = builder.build(xmlStream);
            Element rootNode = document.getRootElement();
            List<Element> list = rootNode.getChildren("produit");

            String sqlInsert = "INSERT INTO Produits (nom, prix, quantite) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sqlInsert);

            System.out.println("--- Contenu du fichier produits.xml ---");

            for (Element node : list) {
                String nom = node.getChildText("nom");
                double prixFournisseur = Double.parseDouble(node.getChildText("prix"));
                int quantite = Integer.parseInt(node.getChildText("quantité"));

                System.out.println("Produit : " + nom + " | Prix : " + prixFournisseur + " | Quantité : " + quantite);

                // Application de la règle métier :
                // La marge commerciale est de 100%, donc le prix de vente correspond au double
                // du prix fournisseur
                double prixVente = prixFournisseur * 2;

                pstmt.setString(1, nom);
                pstmt.setDouble(2, prixVente);
                pstmt.setInt(3, quantite);
                pstmt.executeUpdate();
            }
            System.out.println("Importation des produits terminée avec succès !");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}