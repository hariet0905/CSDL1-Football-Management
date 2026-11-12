import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Supplementary, read-only match statistics. ESPN IDs never become business IDs. */
final class EspnFootball {
    private final FootballData.Source source;
    private final Map<String,Board> boards=new LinkedHashMap<>();
    private record Board(long loadedAt,Map<String,Object> payload) {}
    private static final String BASE="https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/";
    EspnFootball(FootballData.Source source) {
        if(source!=null){this.source=source;return;}
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
        this.source=path->{
            HttpRequest request=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(18))
                .header("Accept","application/json").header("User-Agent","FootballManagement/1.0").GET().build();
            HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream in=response.body()){
                if(response.statusCode()!=200)throw new IOException("ESPN chưa phản hồi thành công (HTTP "+response.statusCode()+").");
                byte[] bytes=in.readNBytes(4*1024*1024+1);
                if(bytes.length>4*1024*1024)throw new IOException("Phản hồi ESPN quá lớn.");
                return obj(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
            }
        };
    }
    Map<String,Object> enrich(Map<String,Object> target,int year)throws Exception {
        if(!"FINISHED".equals(target.get("status"))||!Objects.equals(target.get("year"),year))throw new IllegalArgumentException("Chi tiết chỉ thuộc trận đã kết thúc đúng mùa.");
        String day=instant(target.get("utcDate")).atOffset(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        Board board=boards.get(day);
        if(board==null||System.currentTimeMillis()-board.loadedAt()>600_000){
            board=new Board(System.currentTimeMillis(),source.get("scoreboard?dates="+day));
            if(boards.size()>=32)boards.remove(boards.keySet().iterator().next());
            boards.put(day,board);
        }
        Map<String,Object> event=select(board.payload(),target,year);
        String id=identifier(event.get("id"));
        return normalize(target,source.get("summary?event="+id),event,year);
    }
    static Map<String,Object> select(Map<String,Object> board,Map<String,Object> target,int year) {
        var leagues=objects(board.get("leagues"));
        if(leagues.size()!=1||!"eng.1".equals(leagues.get(0).get("slug")))throw new IllegalArgumentException("Sai giải ESPN.");
        List<Map<String,Object>> matches=new ArrayList<>();
        for(var event:objects(board.get("events"))) {
            if(!Objects.equals(number(obj(event.get("season")).get("year"),0,9999),year))continue;
            var competitions=objects(event.get("competitions"));
            if(competitions.size()==1&&matches(competitions.get(0),target))matches.add(event);
        }
        if(matches.size()!=1)throw new IllegalArgumentException("Chưa có trận ESPN khớp duy nhất với mùa, CLB, giờ đấu và tỷ số.");
        return matches.get(0);
    }
    static Map<String,Object> normalize(Map<String,Object> target,Map<String,Object> payload,Map<String,Object> event,int year) {
        var header=obj(payload.get("header"));String eventId=identifier(event.get("id"));
        if(!eventId.equals(identifier(header.get("id")))||!"eng.1".equals(obj(header.get("league")).get("slug"))
            ||!Objects.equals(number(obj(header.get("season")).get("year"),0,9999),year))throw new IllegalArgumentException("Sai định danh/giải/mùa ESPN.");
        var competitions=objects(header.get("competitions"));
        if(competitions.size()!=1||!matches(competitions.get(0),target)||!eventId.equals(identifier(competitions.get(0).get("id"))))throw new IllegalArgumentException("Chi tiết ESPN không khớp trận.");
        var competition=competitions.get(0);var boardCompetition=objects(event.get("competitions")).get(0);
        Map<String,Object> result=new LinkedHashMap<>(target);Set<String> players=new HashSet<>();boolean supplied=false;
        for(String side:List.of("home","away")) {
            var competitor=competitor(competition,side);String teamId=identifier(obj(competitor.get("team")).get("id"));
            if(!teamId.equals(identifier(obj(competitor(boardCompetition,side).get("team")).get("id"))))throw new IllegalArgumentException("Định danh đội ESPN thay đổi.");
            var team=new LinkedHashMap<>(obj(target.get(side)));
            var stats=uniqueTeam(objects(obj(payload.get("boxscore")).get("teams")),teamId);
            var totals=new LinkedHashMap<>(obj(team.get("statistics")));
            if(stats!=null){
                var values=statistics(stats.get("statistics"));
                Map<String,String> names=Map.ofEntries(Map.entry("possessionPct","ball_possession"),Map.entry("totalShots","shots"),Map.entry("shotsOnTarget","shots_on_goal"),
                    Map.entry("shotsOffTarget","shots_off_goal"),Map.entry("wonCorners","corner_kicks"),Map.entry("foulsCommitted","fouls"),Map.entry("offsides","offsides"),Map.entry("saves","saves"),Map.entry("yellowCards","yellow_cards"),Map.entry("redCards","red_cards"));
                for(var field:names.entrySet()){
                    Number value=field.getValue().equals("ball_possession")?decimal(values.get(field.getKey()),0,100):number(values.get(field.getKey()),0,1000);
                    if(value!=null){totals.put(field.getValue(),value);supplied=true;}
                }
            }
            team.put("statistics",totals);
            var roster=uniqueTeam(objects(payload.get("rosters")),teamId);
            if(roster!=null){
                List<Map<String,Object>> rows=new ArrayList<>();
                for(var player:objects(roster.get("roster"))){
                    var values=statistics(player.get("stats"));Integer appearances=number(values.get("appearances"),0,1);
                    // An unused substitute, including one receiving a bench card, is not an appearance.
                    if(Objects.equals(appearances,0)||appearances==null&&!Boolean.TRUE.equals(player.get("starter"))&&!Boolean.TRUE.equals(player.get("subbedIn")))continue;
                    var athlete=obj(player.get("athlete"));String personId=identifier(athlete.get("id"));
                    if(!players.add(personId))throw new IllegalArgumentException("Cầu thủ ESPN xuất hiện nhiều lần.");
                    Map<String,Object> row=new LinkedHashMap<>();row.put("provider","espn");row.put("providerId",personId);row.put("name",FootballData.text(athlete,"displayName"));
                    row.put("shirt",number(player.get("jersey"),0,999));row.put("position",obj(player.get("position")).get("displayName"));row.put("starter",player.get("starter"));
                    row.put("minutes",minutes(player,values));
                    for(var field:Map.of("totalGoals","goals","goalAssists","assists","yellowCards","yellow","redCards","red","totalShots","shots","shotsOnTarget","shotsOnTarget").entrySet())
                        row.put(field.getValue(),number(values.get(field.getKey()),0,field.getValue().equals("red")?1:field.getValue().equals("yellow")?2:1000));
                    rows.add(row);
                }
                if(!rows.isEmpty()){team.put("players",rows);team.put("playerStatsAvailable",true);team.put("playerProvider","ESPN");supplied=true;}
                if(roster.get("formation") instanceof String)team.put("formation",roster.get("formation"));
            }
            result.put(side,team);
        }
        var venue=obj(obj(payload.get("gameInfo")).get("venue")).get("fullName");
        if(result.get("venue")==null&&venue instanceof String)result.put("venue",venue);
        if(supplied){result.put("statisticsProvider","ESPN");result.put("statisticsUpdatedAt",Instant.now().toString());}
        else result.put("error","ESPN chưa có thống kê cho trận này.");
        return result;
    }
    static Map<String,Object> retain(Map<String,Object> current,Map<String,Object> previous){
        if(!"ESPN".equals(previous.get("statisticsProvider")))return current;
        for(String field:List.of("id","year","utcDate","status","score"))if(!Objects.equals(current.get(field),previous.get(field)))return current;
        for(String side:List.of("home","away"))if(!Objects.equals(obj(current.get(side)).get("id"),obj(previous.get(side)).get("id")))return current;
        var result=new LinkedHashMap<>(current);
        for(String side:List.of("home","away")){
            var team=new LinkedHashMap<>(obj(current.get(side)));var cached=obj(previous.get(side));
            for(String field:List.of("statistics","players","playerStatsAvailable","playerProvider","formation"))if(cached.containsKey(field))team.put(field,cached.get(field));
            result.put(side,team);
        }
        result.put("statisticsProvider",previous.get("statisticsProvider"));result.put("statisticsUpdatedAt",previous.get("statisticsUpdatedAt"));return result;
    }
    private static boolean matches(Map<String,Object> competition,Map<String,Object> target) {
        try {
            if(!Boolean.TRUE.equals(obj(obj(competition.get("status")).get("type")).get("completed")))return false;
            if(Math.abs(Duration.between(instant(competition.get("date")),instant(target.get("utcDate"))).toSeconds())>900)return false;
            var home=competitor(competition,"home");var away=competitor(competition,"away");
            if(identifier(obj(home.get("team")).get("id")).equals(identifier(obj(away.get("team")).get("id"))))return false;
            for(String side:List.of("home","away")){
                var candidate=side.equals("home")?home:away;
                if(!club(FootballData.text(obj(candidate.get("team")),"displayName")).equals(club(FootballData.text(obj(target.get(side)),"name"))))return false;
                if(!Objects.equals(number(candidate.get("score"),0,255),number(obj(target.get("score")).get(side),0,255))||candidate.get("score")==null||obj(target.get("score")).get(side)==null)return false;
            }
            return true;
        }catch(RuntimeException e){return false;}
    }
    private static String club(String name){
        String normalized=FootballData.normalize(name);
        if(normalized.endsWith("fc")&&!normalized.endsWith("afc"))normalized=normalized.substring(0,normalized.length()-2);
        return switch(normalized){case "brightonandhovealbion"->"brightonhovealbion";case "bournemouth"->"afcbournemouth";case "hullcityafc"->"hullcity";case "wolves"->"wolverhamptonwanderers";default->normalized;};
    }
    private static Map<String,Object> competitor(Map<String,Object> competition,String side){
        var teams=objects(competition.get("competitors"));var matching=teams.stream().filter(t->side.equals(t.get("homeAway"))).toList();
        if(teams.size()!=2||matching.size()!=1)throw new IllegalArgumentException("Hai đội ESPN không duy nhất.");return matching.get(0);
    }
    private static Map<String,Object> uniqueTeam(List<Map<String,Object>> rows,String teamId){
        var found=rows.stream().filter(t->teamId.equals(identifier(obj(t.get("team")).get("id")))).toList();
        if(found.size()>1)throw new IllegalArgumentException("Thống kê đội ESPN bị trùng.");return found.isEmpty()?null:found.get(0);
    }
    private static Map<String,Object> statistics(Object raw){
        Map<String,Object> values=new HashMap<>();
        for(var entry:objects(raw)){
            String name=FootballData.text(entry,"name");
            if(values.containsKey(name))throw new IllegalArgumentException("Chỉ số ESPN bị trùng.");
            values.put(name,entry.containsKey("value")?entry.get("value"):entry.get("displayValue"));
        }
        return values;
    }
    private static Integer minutes(Map<String,Object> player,Map<String,Object> values){
        Integer direct=number(values.get("minutesPlayed"),0,90);if(direct!=null)return direct;
        for(String flag:List.of("starter","subbedIn","subbedOut"))if(!(player.get(flag) instanceof Boolean))return null;
        Integer red=number(values.get("redCards"),0,1);if(red==null)return null;
        List<Integer> substitutions=new ArrayList<>(),dismissals=new ArrayList<>();
        for(var play:objects(player.get("plays"))){
            if(!Boolean.TRUE.equals(play.get("substitution"))&&!Boolean.TRUE.equals(play.get("redCard")))continue;
            String clock=Objects.toString(obj(play.get("clock")).get("displayValue"),"");
            if(!clock.matches("[0-9]{1,3}'(?:\\+[0-9]{1,2}')?"))return null;
            int minute=Integer.parseInt(clock.substring(0,clock.indexOf('\'')));if(minute>90)return null;
            if(Boolean.TRUE.equals(play.get("substitution")))substitutions.add(minute);
            if(Boolean.TRUE.equals(play.get("redCard")))dismissals.add(minute);
        }
        boolean starter=Boolean.TRUE.equals(player.get("starter")),in=Boolean.TRUE.equals(player.get("subbedIn")),out=Boolean.TRUE.equals(player.get("subbedOut"));
        if(starter==in||substitutions.size()!=(in?1:0)+(out?1:0)||dismissals.size()!=red)return null;
        Collections.sort(substitutions);int start=starter?0:substitutions.get(0),end=out?substitutions.get(substitutions.size()-1):90;
        if(red==1)end=Math.min(end,dismissals.get(0));return end>=start?end-start:null;
    }
    private static String identifier(Object value){String id=Objects.toString(value,"");if(!id.matches("[1-9][0-9]{0,17}"))throw new IllegalArgumentException("ID ESPN không hợp lệ.");return id;}
    private static Integer number(Object value,int min,int max){
        BigDecimal n=decimal(value,min,max);if(n==null)return null;
        try{return n.intValueExact();}catch(ArithmeticException e){throw new IllegalArgumentException("Chỉ số ESPN không nguyên.");}
    }
    private static BigDecimal decimal(Object value,int min,int max){
        if(value==null||"".equals(value)||"--".equals(value)||"-".equals(value))return null;
        String s=value.toString().trim();if(s.endsWith("%"))s=s.substring(0,s.length()-1);
        try{BigDecimal n=new BigDecimal(s);if(n.compareTo(BigDecimal.valueOf(min))<0||n.compareTo(BigDecimal.valueOf(max))>0)throw new IllegalArgumentException("Chỉ số ESPN ngoài miền.");return n;}
        catch(NumberFormatException e){throw new IllegalArgumentException("Chỉ số ESPN sai định dạng.");}
    }
    private static Instant instant(Object value){return OffsetDateTime.parse(Objects.toString(value,"")).toInstant();}
    private static Map<String,Object> obj(Object value){return FootballData.obj(value);}
    private static List<Map<String,Object>> objects(Object value){return FootballData.objects(value);}
}
