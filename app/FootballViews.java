import java.time.*;
import java.util.*;

/** Read-only provider projections. Nothing in this class writes business records. */
final class FootballViews {
    static Map<String,Object> form(Map<String,Object> standings,Map<String,Object> payload,int year) {
        List<Map<String,Object>> finished=new ArrayList<>();Set<Long> seen=new HashSet<>();
        if(!(payload.get("matches") instanceof List<?>))throw new IllegalArgumentException("Thiếu kết quả EPL.");
        for(Map<String,Object> match:FootballData.objects(payload.get("matches"))) {
            if(!"FINISHED".equals(match.get("status")))continue;
            validate(match,year);long id=FootballData.id(match);
            if(!seen.add(id))throw new IllegalArgumentException("Trận trùng trong phong độ.");
            Map<String,Object> score=FootballData.obj(FootballData.obj(match.get("score")).get("fullTime"));
            Map<String,Object> home=FootballData.obj(match.get("homeTeam")),away=FootballData.obj(match.get("awayTeam"));
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("utcDate",Instant.parse(FootballData.text(match,"utcDate")).toString());
            row.put("homeId",FootballData.id(home));row.put("awayId",FootballData.id(away));row.put("home",name(home));row.put("away",name(away));
            row.put("homeGoals",FootballData.integer(score,"home",0,255));row.put("awayGoals",FootballData.integer(score,"away",0,255));finished.add(row);
        }
        finished.sort(Comparator.comparing((Map<String,Object> r)->(String)r.get("utcDate")).thenComparingLong(r->((Number)r.get("id")).longValue()).reversed());
        Map<String,Object> result=new LinkedHashMap<>(standings);List<Map<String,Object>> rows=new ArrayList<>();
        for(Map<String,Object> team:FootballData.objects(standings.get("table"))) {
            long teamId=((Number)team.get("teamId")).longValue();Map<String,Object> row=new LinkedHashMap<>(team);List<Map<String,Object>> recent=new ArrayList<>();
            for(Map<String,Object> match:finished) {
                boolean home=((Number)match.get("homeId")).longValue()==teamId;
                if(!home&&((Number)match.get("awayId")).longValue()!=teamId)continue;
                Map<String,Object> item=new LinkedHashMap<>(match);int difference=(int)match.get("homeGoals")-(int)match.get("awayGoals");if(!home)difference=-difference;
                item.put("outcome",difference>0?"W":difference<0?"L":"D");recent.add(item);if(recent.size()==5)break;
            }
            Collections.reverse(recent);row.put("recentMatches",recent);row.put("form",recent.stream().map(m->m.get("outcome")).toList());rows.add(row);
        }
        result.put("table",rows);result.put("formUpdatedAt",Instant.now().toString());return result;
    }
    static void validate(Map<String,Object> match,int year) {
        if(!"PL".equals(FootballData.obj(match.get("competition")).get("code"))||LocalDate.parse(FootballData.text(FootballData.obj(match.get("season")),"startDate")).getYear()!=year)throw new IllegalArgumentException("Trận không thuộc EPL/mùa đã chọn.");
    }
    static String name(Map<String,Object> team){return FootballData.teamName(team);}
    static Map<String,Object> match(Map<String,Object> source,long id,int year) {
        validate(source,year);if(FootballData.id(source)!=id)throw new IllegalArgumentException("Sai định danh trận.");
        Map<String,Object> out=new LinkedHashMap<>();out.put("id",id);out.put("year",year);out.put("running",false);out.put("status",source.get("status"));out.put("utcDate",Instant.parse(FootballData.text(source,"utcDate")).toString());
        out.put("venue",source.get("venue"));out.put("matchday",source.get("matchday"));
        Map<String,Object> score=FootballData.obj(source.get("score"));out.put("score",score.get("fullTime"));out.put("halfTime",score.get("halfTime"));
        List<String> referees=FootballData.objects(source.get("referees")).stream().map(r->Objects.toString(r.get("name"),"")).filter(n->!n.isBlank()).toList();out.put("referees",referees);
        out.put("home",side(source,true));out.put("away",side(source,false));out.put("updatedAt",Instant.now().toString());return out;
    }
    private static Map<String,Object> side(Map<String,Object> match,boolean home) {
        Map<String,Object> team=FootballData.obj(match.get(home?"homeTeam":"awayTeam")),out=new LinkedHashMap<>();out.put("id",FootballData.id(team));out.put("name",name(team));out.put("formation",team.get("formation"));
        Map<Long,FootballData.Appearance> statistics=new HashMap<>();
        if(FootballData.hasDetails(match,home))try{for(var appearance:FootballData.appearances(match,home))statistics.put(appearance.providerId(),appearance);}catch(IllegalArgumentException ignored){}
        Map<Long,Map<String,Object>> people=new LinkedHashMap<>();
        for(String key:List.of("lineup","bench"))for(Map<String,Object> player:FootballData.objects(team.get(key)))people.put(FootballData.id(player),player);
        List<Map<String,Object>> players=new ArrayList<>();
        for(var item:people.entrySet()) {
            var stats=statistics.get(item.getKey());
            if(stats==null&&!FootballData.objects(team.get("lineup")).contains(item.getValue()))continue;
            Map<String,Object> player=new LinkedHashMap<>();player.put("name",item.getValue().get("name"));player.put("shirt",item.getValue().get("shirtNumber"));player.put("position",item.getValue().get("position"));
            player.put("minutes",stats==null?null:stats.minutes());player.put("goals",stats==null?null:stats.goals());player.put("assists",stats==null?null:stats.assists());player.put("yellow",stats==null?null:stats.yellow());player.put("red",stats==null?null:stats.red());players.add(player);
        }
        out.put("players",players);out.put("playerStatsAvailable",!statistics.isEmpty());
        Map<String,Object> totals=new LinkedHashMap<>(),raw=FootballData.obj(team.get("statistics"));
        for(String field:List.of("ball_possession","shots","shots_on_goal","shots_off_goal","corner_kicks","fouls","offsides","saves","yellow_cards","red_cards")) {
            if(raw.get(field) instanceof Number)totals.put(field,FootballData.integer(raw,field,0,field.equals("ball_possession")?100:1000));
        }
        out.put("statistics",totals);return out;
    }
}
