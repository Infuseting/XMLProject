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

/**
 * Gère l'ingestion et le traitement d'une commande client reçue au format XML.
 * Centralise les règles métier de :
 * - Validation fonctionnelle et validation DTD de la commande
 * - Vérification stricte des disponibilités en stock avant facturation
 * - Gestion transactionnelle : "Tout ou rien" (rollback en cas de stock
 * insuffisant ou erreur)
 */
public class GestionCommandes {

    private static final String DB_URL = App.db_url;
    private static final String DB_USER = App.db_user;
    private static final String DB_PASS = App.db_pass;

    /**
     * Lit, valide et traite la commande décrite dans "commande.xml".
     * Règles métier du processus de commande :
     * 1. La commande doit être structurellement valide (DTD).
     * 2. Le client est identifié par son email (récupéré ou créé dynamiquement).
     * 3. Avant tout enregistrement, TROP de stock doit être disponible pour TOUS
     * les produits demandés.
     * 4. Si la commande est valide, les stocks sont décrémentés et le panier
     * enregistré de manière atomique (transactionnelle).
     */
    public void traiterCommande() {
        SAXBuilder builder = new SAXBuilder(XMLReaders.DTDVALIDATING);

        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) {
                if (systemId.contains("commande.dtd")) {
                    java.io.InputStream dtdStream = GestionCommandes.class.getClassLoader()
                            .getResourceAsStream("commande.dtd");
                    return new InputSource(dtdStream);
                }
                return null;
            }
        });

        java.net.URL xmlUrl = GestionCommandes.class.getClassLoader().getResource("commande.xml");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Début de la logique métier transactionnelle :
            // On désactive l'auto-commit. Si l'une des validations métier échoue (ex: stock
            // insuffisant),
            // aucune donnée métier (client, commande, ligne, modif stock) ne sera
            // enregistrée en base.
            conn.setAutoCommit(false);

            try {
                Document document = builder.build(xmlUrl);
                Element rootNode = document.getRootElement();

                Element clientNode = rootNode.getChild("client");
                String email = clientNode.getChildText("email").trim();
                String nomClient = clientNode.getChildText("nom-client").trim();
                String ville = clientNode.getChildText("ville").trim();

                // Logique métier : Identifier le client par son email ou l'inscrire s'il s'agit
                // de sa première commande
                int clientId = obtenirOuCreerClient(conn, nomClient, email, ville);

                List<Element> produits = rootNode.getChild("produits").getChildren("produit");
                double totalCommande = 0;

                for (Element prodNode : produits) {
                    String nomProduit = prodNode.getChildText("nom").trim();
                    int quantiteDemandee = Integer.parseInt(prodNode.getChildText("quantité").trim());

                    // Règle métier : On interdit la commande de quantités nulles ou négatives
                    if (quantiteDemandee <= 0) {
                        throw new Exception("Quantité demandée invalide pour " + nomProduit);
                    }

                    // Règle métier : Validation préventive
                    // On vérifie le stock disponible AVANT d'insérer quoi que ce soit.
                    // Toute anomalie (produit inexistant ou stock insuffisant) fera crasher la
                    // transaction.
                    int stockDisponible = verifierStock(conn, nomProduit);
                    if (quantiteDemandee > stockDisponible) {
                        throw new Exception("Stock insuffisant pour " + nomProduit + ". Demandé: " + quantiteDemandee
                                + ", En stock: " + stockDisponible);
                    }
                }

                String date = rootNode.getChildText("date").trim();
                int commandeId = insererCommande(conn, clientId, date);

                for (Element prodNode : produits) {
                    String nomProduit = prodNode.getChildText("nom").trim();
                    double prix = Double.parseDouble(prodNode.getChildText("prix").trim());
                    int quantite = Integer.parseInt(prodNode.getChildText("quantité").trim());
                    int produitId = obtenirIdProduit(conn, nomProduit);
                    // Chaque produit validé de la commande est inséré et le stock est débité
                    insererLigneCommande(conn, commandeId, produitId, quantite, prix);
                    mettreAJourStock(conn, produitId, quantite);

                    // Cumul du montant total du panier (Règle de facturation simple : somme des
                    // produits * qte)
                    totalCommande += (prix * quantite);
                }

                mettreAJourTotalCommande(conn, commandeId, totalCommande);

                // Si on arrive ici, l'ensemble des règles métier étaient respectées, on valide
                // définitivement la transaction
                conn.commit();
                System.out.println("Commande traitée et insérée avec succès !");

            } catch (Exception e) {
                // Logique métier : En cas d'erreur métier ou technique, on annule tous les
                // changements (sauvegarde la consistance des stocks)
                conn.rollback();
                System.err.println("Erreur lors du traitement. La commande a été annulée. Raison : " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Logique applicative : Garantit l'unicité du client selon son adresse e-mail.
     * Retourne son identifiant s'il existe, ou le crée à la volée s'il est inconnu.
     */
    private int obtenirOuCreerClient(Connection conn, String nom, String email, String ville) throws SQLException {
        String query = "SELECT id FROM Clients WHERE email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                return rs.getInt("id");
        }
        String insert = "INSERT INTO Clients (nom, email, ville) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nom);
            stmt.setString(2, email);
            stmt.setString(3, ville);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next())
                return rs.getInt(1);
        }
        throw new SQLException("Impossible de créer le client.");
    }

    private int verifierStock(Connection conn, String nomProduit) throws SQLException, Exception {
        String query = "SELECT quantite FROM Produits WHERE nom = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nomProduit);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                return rs.getInt("quantite");
        }
        throw new Exception("Produit inconnu en base : " + nomProduit);
    }

    private int obtenirIdProduit(Connection conn, String nomProduit) throws SQLException {
        String query = "SELECT id FROM Produits WHERE nom = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nomProduit);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                return rs.getInt("id");
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
            if (rs.next())
                return rs.getInt(1);
        }
        throw new SQLException("Échec création commande.");
    }

    private void insererLigneCommande(Connection conn, int cmdId, int prodId, int qte, double prix)
            throws SQLException {
        String insert = "INSERT INTO Lignes_Commande (id_commande, id_produit, quantite, prix_unitaire) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setInt(1, cmdId);
            stmt.setInt(2, prodId);
            stmt.setInt(3, qte);
            stmt.setDouble(4, prix);
            stmt.executeUpdate();
        }
    }

    /**
     * Met à jour les stocks en base après achat.
     * Règle métier : un achat diminue la quantité disponible sans limite basse à ce
     * stade
     * (la limite basse est vérifiée en amont par verifierStock()).
     */
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
