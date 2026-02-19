CREATE TABLE Clients (
     id INT AUTO_INCREMENT PRIMARY KEY,
     nom VARCHAR(100) NOT NULL,
     email VARCHAR(150) UNIQUE NOT NULL,
     ville VARCHAR(100)
);

CREATE TABLE Produits (
      id INT AUTO_INCREMENT PRIMARY KEY,
      nom VARCHAR(100) NOT NULL,
      prix DOUBLE NOT NULL,
      quantite INT NOT NULL
);

CREATE TABLE Commandes (
   id INT AUTO_INCREMENT PRIMARY KEY,
   id_client INT NOT NULL,
   date_commande VARCHAR(20),
   total DOUBLE,
   FOREIGN KEY (id_client) REFERENCES Clients(id)
);

CREATE TABLE Lignes_Commande (
     id_commande INT NOT NULL,
     id_produit INT NOT NULL,
     quantite INT NOT NULL,
     prix_unitaire DOUBLE NOT NULL,
     PRIMARY KEY (id_commande, id_produit),
     FOREIGN KEY (id_commande) REFERENCES Commandes(id),
     FOREIGN KEY (id_produit) REFERENCES Produits(id)
);