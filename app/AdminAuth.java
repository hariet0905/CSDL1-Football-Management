import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** One local administrator. No business tables, plaintext password, or persistent sessions. */
final class AdminAuth {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final int ITERATIONS=600_000;
    private static final Duration SESSION_AGE=Duration.ofHours(8),WINDOW=Duration.ofMinutes(15);
    private final String username;
    private final byte[] salt,expected;
    private final Clock clock;
    private final Map<String,Instant> sessions=new HashMap<>();
    private final ArrayDeque<Instant> failures=new ArrayDeque<>();
    record Login(String token,boolean throttled) {}

    AdminAuth(String username,byte[] salt,byte[] expected,Clock clock) {
        this.username=username;this.salt=salt.clone();this.expected=expected.clone();this.clock=clock;
    }
    static AdminAuth open(Path root) throws Exception {
        Path file=root.resolve("app/.admin.properties");
        if(!Files.exists(file)) setup(file);
        Properties p=new Properties();try(Reader r=Files.newBufferedReader(file)){p.load(r);}
        if(!"PBKDF2WithHmacSHA256".equals(p.getProperty("algorithm")) || !String.valueOf(ITERATIONS).equals(p.getProperty("iterations"))) throw new IOException("Cau hinh dang nhap khong hop le.");
        String user=p.getProperty("username","");byte[] salt=Base64.getDecoder().decode(p.getProperty("salt","")),hash=Base64.getDecoder().decode(p.getProperty("hash",""));
        if(user.isBlank()||salt.length!=32||hash.length!=32)throw new IOException("Cau hinh dang nhap khong hop le.");
        return new AdminAuth(user,salt,hash,Clock.systemUTC());
    }
    private static void setup(Path file) throws Exception {
        Console console=System.console();
        if(console==null)throw new IOException("Chay app\\setup-admin.bat trong Terminal de tao tai khoan quan tri.");
        console.printf("Tao tai khoan quan tri (luu ngoai DB).%n");
        String user=console.readLine("Ten dang nhap: ");
        if(user==null||!user.matches("[a-zA-Z0-9._-]{3,40}"))throw new IllegalArgumentException("Ten dang nhap can 3-40 ky tu: chu, so, dau cham, gach ngang.");
        char[] password=console.readPassword("Mat khau (it nhat 12 ky tu): "),confirm=console.readPassword("Nhap lai mat khau: ");
        try {
            if(password==null||confirm==null||password.length<12||password.length>256||!Arrays.equals(password,confirm))throw new IllegalArgumentException("Mat khau khong hop le hoac khong trung nhau.");
            byte[] salt=new byte[32];RANDOM.nextBytes(salt);
            Properties p=new Properties();p.setProperty("username",user);p.setProperty("algorithm","PBKDF2WithHmacSHA256");p.setProperty("iterations",String.valueOf(ITERATIONS));p.setProperty("salt",Base64.getEncoder().encodeToString(salt));p.setProperty("hash",Base64.getEncoder().encodeToString(hash(password,salt)));
            Files.createDirectories(file.getParent());
            try(Writer w=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){p.store(w,"Local administrator password hash. Do not commit.");}
            console.printf("Da tao tai khoan quan tri.%n");
        } finally {if(password!=null)Arrays.fill(password,'\0');if(confirm!=null)Arrays.fill(confirm,'\0');}
    }
    static byte[] hash(char[] password,byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    synchronized Login login(String user,char[] password) throws GeneralSecurityException {
        Instant now=clock.instant();failures.removeIf(t->!t.plus(WINDOW).isAfter(now));prune(now);
        try {
            if(failures.size()>=5)return new Login(null,true);
            boolean valid=MessageDigest.isEqual(expected,hash(password,salt));
            if(!valid||!username.equals(user)){failures.add(now);return new Login(null,false);}
            failures.clear();byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);
            String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            // A single administrator has one active browser session.
            sessions.clear();sessions.put(token,now.plus(SESSION_AGE));return new Login(token,false);
        }finally{Arrays.fill(password,'\0');}
    }
    synchronized boolean authenticated(String token){prune(clock.instant());return token!=null&&sessions.containsKey(token);}
    synchronized void logout(String token){sessions.remove(token);}
    private void prune(Instant now){sessions.values().removeIf(expires->!expires.isAfter(now));}
    String username(){return username;}
    static String cookieToken(String cookie){
        if(cookie==null)return null;String found=null;
        for(String part:cookie.split(";")){String[] pair=part.trim().split("=",2);if(pair[0].equals("csdl_session")){if(found!=null||pair.length!=2||!pair[1].matches("[A-Za-z0-9_-]{43}"))return null;found=pair[1];}}
        return found;
    }
    public static void main(String[] args) throws Exception {open(Path.of(args.length==0?"..":args[0]).toAbsolutePath().normalize());}
}
