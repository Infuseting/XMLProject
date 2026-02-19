package fr.infuseting;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.InputStream;
import java.sql.*;
import java.util.List;

public class GestionCommandes {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/mini_projet";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root"; // Mot de passe défini dans Docker

    public void traiterCommande() {
        SAXBuilder builder = new SAXBuilder(XMLReaders.DTDVALIDATING);

        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) {
                if (systemId.contains("commande.dtd")) {
                    java.io.InputStream dtdStream = GestionCommandes.class.getClassLoader().getResourceAsStream("commande.dtd");
                    return new  InputSource(dtdStream);
                }
                return null;
            }
        });

        java.net.URL xmlUrl = GestionCommandes.class.getClassLoader().getResource("commande.xml");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);

            try {
                Document document = builder.build(xmlUrl);
                Element rootNode = document.getRootElement();

                Element clientNode = rootNode.getChild("client");
                String email = clientNode.getChildText("email").trim();
                String nomClient = clientNode.getChildText("nom-client").trim();
                String ville = clientNode.getChildText("ville").trim();

                int clientId = obtenirOuCreerClient(conn, nomClient, email, ville);

                List<Element> produits = rootNode.getChild("produits").getChildren("produit");
                double totalCommande = 0;

                for (Element prodNode : produits) {
                    String nomProduit = prodNode.getChildText("nom").trim();
                    int quantiteDemandee = Integer.parseInt(prodNode.getChildText("quantité").trim());

                    if (quantiteDemandee <= 0) {
                        throw new Exception("Quantité demandée invalide pour " + nomProduit);
                    }

                    int stockDisponible = verifierStock(conn, nomProduit);
                    if (quantiteDemandee > stockDisponible) {
                        throw new Exception("Stock insuffisant pour " + nomProduit + ". Demandé: " + quantiteDemandee + ", En stock: " + stockDisponible);
                    }
                }

                String date = rootNode.getChildText("date").trim();
                int commandeId = insererCommande(conn, clientId, date);

                for (Element prodNode : produits) {
                    String nomProduit = prodNode.getChildText("nom").trim();
                    double prix = Double.parseDouble(prodNode.getChildText("prix").trim());
                    int quantite = Integer.parseInt(prodNode.getChildText("quantité").trim());
                    int produitId = obtenirIdProduit(conn, nomProduit);
                    insererLigneCommande(conn, commandeId, produitId, quantite, prix);
                    mettreAJourStock(conn, produitId, quantite);

                    totalCommande += (prix * quantite);
                }

                mettreAJourTotalCommande(conn, commandeId, totalCommande);

                conn.commit();
                System.out.println("Commande traitée et insérée avec succès !");

            } catch (Exception e) {
                conn.rollback();
                System.err.println("Erreur lors du traitement. La commande a été annulée. Raison : " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private int obtenirOuCreerClient(Connection conn, String nom, String email, String ville) throws SQLException {
        String query = "SELECT id FROM Clients WHERE email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        String insert = "INSERT INTO Clients (nom, email, ville) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nom);
            stmt.setString(2, email);
            stmt.setString(3, ville);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        }
        throw new SQLException("Impossible de créer le client.");
    }

    private int verifierStock(Connection conn, String nomProduit) throws SQLException, Exception {
        String query = "SELECT quantite FROM Produits WHERE nom = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nomProduit);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("quantite");
        }
        throw new Exception("Produit inconnu en base : " + nomProduit);
    }

    private int obtenirIdProduit(Connection conn, String nomProduit) throws SQLException {
        String query = "SELECT id FROM Produits WHERE nom = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nomProduit);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        return -1;
    }

    private int insererCommande(Connection conn, int clientId, String date) throws SQLException {
        String insert = "INSERT INTO Commandes (id_client, date_commande) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, clientId);
            stmt.setString(2, date);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        }
        throw new SQLException("Échec création commande.");
    }

    private void insererLigneCommande(Connection conn, int cmdId, int prodId, int qte, double prix) throws SQLException {
        String insert = "INSERT INTO Lignes_Commande (id_commande, id_produit, quantite, prix_unitaire) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setInt(1, cmdId);
            stmt.setInt(2, prodId);
            stmt.setInt(3, qte);
            stmt.setDouble(4, prix);
            stmt.executeUpdate();
        }
    }

    private void mettreAJourStock(Connection conn, int prodId, int qteAchetee) throws SQLException {
        String update = "UPDATE Produits SET quantite = quantite - ? WHERE id = ?"; // Mise à jour requise [cite: 28]
        try (PreparedStatement stmt = conn.prepareStatement(update)) {
            stmt.setInt(1, qteAchetee);
            stmt.setInt(2, prodId);
            stmt.executeUpdate();
        }
    }

    private void mettreAJourTotalCommande(Connection conn, int cmdId, double total) throws SQLException {
        String update = "UPDATE Commandes SET total = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(update)) {
            stmt.setDouble(1, total);
            stmt.setInt(2, cmdId);
            stmt.executeUpdate();
        }
    }
}
