package galgeleg;

import java.util.HashMap;
import javax.jws.WebService;
import brugerautorisation.data.Bruger;
import brugerautorisation.transport.rmi.Brugeradmin;
import db.Connector;
import java.rmi.Naming;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebService(endpointInterface = "galgeleg.GalgeI")
public class Galgelogik extends UnicastRemoteObject implements GalgeI {
    
    ArrayList<String> muligeOrd = new ArrayList<String>();
    private String ordet;
    private ArrayList<String> brugteBogstaver = new ArrayList<String>();
    private String synligtOrd;
    private int antalForkerteBogstaver;
    private boolean sidsteBogstavVarKorrekt;
    private boolean spilletErVundet;
    private boolean spilletErTabt;
    private Brugeradmin BA;
    static HashMap<String, Galgeleg> brugere;
    private int scoren;
    private int[] Highscore = new int[10];
    private int[] revHighscore = new int[10];
    private int[] sortedHighscore = new int[10];
    private Connection conn = null;
    private Connector connector;
    
    public ArrayList<String> getBrugteBogstaver(String bruger) {
        Galgeleg g = brugere.get(bruger);
        return g.brugteBogstaver;
    }
    
    public String getSynligtOrd(String bruger) {
        Galgeleg g = brugere.get(bruger);
        return g.synligtOrd;
    }
    
    public String getOrdet(String bruger) {
        Galgeleg g = brugere.get(bruger);
        return g.ordet;
    }
    
    public int getAntalForkerteBogstaver(String bruger) {
        Galgeleg g = brugere.get(bruger);
        return g.antalForkerteBogstaver;
    }
    
    public boolean erSidsteBogstavKorrekt(String bruger) {
        Galgeleg g = brugere.get(bruger);
        return g.sidsteBogstavVarKorrekt;
    }
    
    public boolean erSpilletVundet(String bruger) {
        Galgeleg g = brugere.get(bruger);
        return g.spilletErVundet;
    }
    
    public boolean erSpilletTabt(String bruger) {
        Galgeleg g = brugere.get(bruger);
        return g.spilletErTabt;
    }
    
    public boolean erSpilletSlut(String bruger) {
        Galgeleg g = brugere.get(bruger);
        return g.spilletErTabt || g.spilletErVundet;
    }
    
    public Galgelogik() throws java.rmi.RemoteException {
        muligeOrd.add("bil");
        muligeOrd.add("computer");
        muligeOrd.add("programmering");
        muligeOrd.add("motorvej");
        muligeOrd.add("busrute");
        muligeOrd.add("gangsti");
        muligeOrd.add("skovsnegl");
        muligeOrd.add("solsort");
        muligeOrd.add("seksten");
        muligeOrd.add("sytten");
        brugere = new HashMap<>();
    }
    
    public void nulstil(String bruger) {
        Galgeleg g = brugere.get(bruger);
        g.brugteBogstaver.clear();
        g.antalForkerteBogstaver = 0;
        g.spilletErVundet = false;
        g.spilletErTabt = false;
        g.ordet = muligeOrd.get(new Random().nextInt(muligeOrd.size()));
        g.opdaterSynligtOrd();
        hentOrdFraDr(bruger);
    }
    
    public void opdaterSynligtOrd(String bruger) {
        Galgeleg g = brugere.get(bruger);
        g.synligtOrd = "";
        g.spilletErVundet = true;
        for (int n = 0; n < g.ordet.length(); n++) {
            String bogstav = g.ordet.substring(n, n + 1);
            if (g.brugteBogstaver.contains(bogstav)) {
                g.synligtOrd = g.synligtOrd + bogstav;
            } else {
                g.synligtOrd = g.synligtOrd + "*";
                g.spilletErVundet = false;
            }
        }
    }
    
    public void gætBogstav(String bogstav, String bruger) {
        Galgeleg g = brugere.get(bruger);
        if (bogstav.length() != 1) {
            return;
        }
        System.out.println("Der gættes på bogstavet: " + bogstav);
        if (g.brugteBogstaver.contains(bogstav)) {
            return;
        }
        if (g.spilletErVundet || g.spilletErTabt) {
            return;
        }
        
        g.brugteBogstaver.add(bogstav);
        
        if (g.ordet.contains(bogstav)) {
            g.sidsteBogstavVarKorrekt = true;
            //System.out.println("Bogstavet var korrekt: " + bogstav);
        } else {
            // Vi gættede på et bogstav der ikke var i ordet.
            g.sidsteBogstavVarKorrekt = false;
            //System.out.println("Bogstavet var IKKE korrekt: " + bogstav);
            g.antalForkerteBogstaver = g.antalForkerteBogstaver + 1;
            if (g.antalForkerteBogstaver >= 6) {
                g.spilletErTabt = true;
            }
        }
        g.opdaterSynligtOrd();
    }
    
    public void logStatus(String bruger) {
        Galgeleg g = brugere.get(bruger);
        System.out.println("---------- ");
        System.out.println("- ordet (skult) = " + ordet);
        System.out.println("- scoren = " + scoren);
        System.out.println("- synligtOrd = " + synligtOrd);
        System.out.println("- forkerteBogstaver = " + antalForkerteBogstaver);
        System.out.println("- brugeBogstaver = " + brugteBogstaver);
        if (g.spilletErTabt) {
            System.out.println("- SPILLET ER TABT");
        }
        if (g.spilletErVundet) {
            System.out.println("- SPILLET ER VUNDET");
        }
        System.out.println("---------- ");
    }
    
    public boolean hentBruger(String brugernavn, String adgangskode) {
        try {
            BA = (Brugeradmin) Naming.lookup("rmi://javabog.dk/brugeradmin");
            Bruger b = BA.hentBruger(brugernavn, adgangskode);
            
            //if user is not already in hashMap, we add him to map and to DB
            if (brugere.containsKey(brugernavn) == false) {
                brugere.put(brugernavn, new Galgeleg());
                System.out.println("Vi er ikke kendte, vi bliver sat i listen");
                System.out.println(brugere.size());
                
                //db connection
                conn = connector.getConnection();
                //check if user is db
                PreparedStatement st = conn.prepareStatement("select * from USERS where username = ?" );
                st.setString(1, brugernavn);
                ResultSet r1=st.executeQuery();
                if(r1.next()) {
                } //if resultset has a value, user is already there
                else {
                    //if not we add him
                    st = conn.prepareStatement("insert into USERS (username,name, highscore) values(?,?,?)");
                    st.setString(1, brugernavn);
                    st.setString(2, b.fornavn);
                    st.setInt(3, 10);
                    st.executeUpdate();
                    conn.close();
                }
            } else {
                brugere.get(brugernavn);
                System.out.println("Vi er kendte, vi henter objekt");
            }
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return false;
    }
 
    public static String hentUrl(String url) throws IOException {
        System.out.println("Henter data fra " + url);
        BufferedReader br = new BufferedReader(new InputStreamReader(new URL(url).openStream()));
        StringBuilder sb = new StringBuilder();
        String linje = br.readLine();
        while (linje != null) {
            sb.append(linje + "\n");
            linje = br.readLine();
        }
        return sb.toString();
    }
    
    public void hentOrdFraDr(String bruger) throws RuntimeException {
        String data = null;
        try {
            data = hentUrl("https://dr.dk");
            //System.out.println("data = " + data);
        } catch (IOException ex) {
            Logger.getLogger(Galgelogik.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        data = data.substring(data.indexOf("<body")). // fjern headere
                replaceAll("<.+?>", " ").toLowerCase(). // fjern tags
                replaceAll("&#198;", "æ"). // erstat HTML-tegn
                replaceAll("&#230;", "æ"). // erstat HTML-tegn
                replaceAll("&#216;", "ø"). // erstat HTML-tegn
                replaceAll("&#248;", "ø"). // erstat HTML-tegn
                replaceAll("&oslash;", "ø"). // erstat HTML-tegn
                replaceAll("&#229;", "å"). // erstat HTML-tegn
                replaceAll("[^a-zæøå]", " "). // fjern tegn der ikke er bogstaver
                replaceAll(" [a-zæøå] ", " "). // fjern 1-bogstavsord
                replaceAll(" [a-zæøå][a-zæøå] ", " "); // fjern 2-bogstavsord
        
        //System.out.println("data = " + data);
        //System.out.println("data = " + Arrays.asList(data.split("\\s+")));
        muligeOrd.clear();
        muligeOrd.addAll(new HashSet<String>(Arrays.asList(data.split(" "))));
        
        System.out.println("muligeOrd = " + muligeOrd);
        Galgeleg g = brugere.get(bruger);
        g.brugteBogstaver.clear();
        g.antalForkerteBogstaver = 0;
        g.spilletErVundet = false;
        g.spilletErTabt = false;
        g.ordet = muligeOrd.get(new Random().nextInt(muligeOrd.size()));
        g.opdaterSynligtOrd();
    }
    
    public void highscoreCheck(String bruger, int score) throws ClassNotFoundException, SQLException {
        int oldHighscore;
        //db connection
        conn = connector.getConnection();
        PreparedStatement st = conn.prepareStatement("select highscore from USERS where username = ?");
        st.setString(1, bruger);
        ResultSet rs = st.executeQuery();
        if (rs.next()) {
            oldHighscore = rs.getInt(1);
            System.out.println(oldHighscore);
            if (score < oldHighscore) {
                System.out.println("Ny highscore!");
                st = conn.prepareStatement("update USERS set highscore = ? where username = ?");
                st.setInt(1, score);
                st.setString(2, bruger);
                st.executeUpdate();
                conn.close();
            }
        }
    }
    
    
    public String getName(String username) throws RemoteException, ClassNotFoundException, SQLException {
        
        conn = connector.getConnection();
        PreparedStatement st = conn.prepareStatement("select name from USERS where username = ?");
        st.setString(1, username);
        ResultSet rs = st.executeQuery();
        rs.next();
        String name = rs.getString(1);
        conn.close();
        return name;
    }
    
    public int getPersonalHighscore(String username)throws RemoteException, ClassNotFoundException, SQLException {
        conn = connector.getConnection();
        PreparedStatement st = conn.prepareStatement("select highscore from USERS where username = ?");
        st.setString(1, username);
        ResultSet rs = st.executeQuery();
        rs.next();
        int highscore = rs.getInt(1);
        conn.close();
        return highscore;  
    }
    
    public String[][] getHighscores() throws java.rmi.RemoteException, ClassNotFoundException, SQLException {
        String[][] highscores = new String[2][10];
        conn = connector.getConnection();
        Statement st = null;
        st = conn.createStatement();
        ResultSet rs = st.executeQuery("select name, highscore from USERS order by highscore;");
        rs.beforeFirst();
        for (int i = 0; i < 10; i++) {
            if (rs.next()) {
                highscores[0][i] = rs.getString("name");
                highscores[1][i] = rs.getString("highscore");
            }
        }
        return highscores;
    }
}
