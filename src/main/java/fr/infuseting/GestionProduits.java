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

public class GestionProduits {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/mini_projet?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root";


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