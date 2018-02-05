/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitch;

import com.github.fedy2.weather.YahooWeatherService;
import com.github.fedy2.weather.YahooWeatherService.LimitDeclaration;
import com.github.fedy2.weather.data.unit.DegreeUnit;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PendingResult;
import com.google.maps.TimeZoneApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fedy2.weather.data.Channel;
import com.github.fedy2.weather.data.Forecast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.maps.model.AddressComponent;
import com.omertron.omdbapi.OMDBException;
import com.omertron.omdbapi.OmdbApi;
import com.omertron.omdbapi.model.OmdbVideoBasic;
import com.omertron.omdbapi.model.OmdbVideoFull;
import com.omertron.omdbapi.model.SearchResults;
import com.omertron.omdbapi.tools.OmdbBuilder;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.xml.bind.JAXBException;

@Controller
@SpringBootApplication
public class Main {

//  @Value("${spring.datasource.url}")
//  private String dbUrl;
//
//  @Autowired
//  private DataSource dataSource;
    final String regexMovie = "(\\w+\\D+)";
    final String regexYear = "(\\w+\\d)";
    final String regexCity = "(\\w+\\D)";
    final String regexRegion = "(\\w+)$";
    final String regexPlot = "(-plot) (\\w+\\D+)";
    final String regexCast = "(-cast) (\\w+\\D+)";
    final String regexFor = "(-fc) ([a-zA-Z]{3}) (\\w+\\D+)";
    final Pattern patternP = Pattern.compile(regexPlot, Pattern.CASE_INSENSITIVE);
    final Pattern patternM = Pattern.compile(regexMovie, Pattern.CASE_INSENSITIVE);
    final Pattern patternY = Pattern.compile(regexYear, Pattern.CASE_INSENSITIVE); 
    final Pattern patternCast = Pattern.compile(regexCast, Pattern.CASE_INSENSITIVE);
    final Pattern patternForecast = Pattern.compile(regexFor, Pattern.CASE_INSENSITIVE);
    final Pattern patternC = Pattern.compile(regexCity, Pattern.CASE_INSENSITIVE);
    final Pattern patternR = Pattern.compile(regexRegion, Pattern.CASE_INSENSITIVE);

  public static void main(String[] args) throws Exception {
    SpringApplication.run(Main.class, args);
  }

  @RequestMapping("/weather")
  @ResponseBody
  String index(Map<String, Object> response, @RequestParam(value="city", required=true) String city) throws IOException {
      String report = "";
      String region="";
      
      final Matcher matcherForecast = patternForecast.matcher(city);
      final Matcher matcherRegion = patternR.matcher(city);

    while (matcherRegion.find()){
    for (int i =0; i < matcherRegion.groupCount(); i++) {
    region += matcherRegion.group(i);
    }
    }
    city = city.replace(region,"");
    
    if(matcherForecast.find()){
        try{
        return forecast(matcherForecast, city,report,region);
        } catch (JAXBException ex) {
              Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
          }
    }else
      try {
          return temperature(city,report,region);
         
      } catch (JAXBException | IOException ex) {
          Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
      }
      return report;
  }

  @RequestMapping("/time")
  @ResponseBody
  String time(@RequestParam(value="city", required=true) String city,
		@RequestParam(value="region", required=false) String region){
      //Setting up crap
      String time = null;
      GeoApiContext context = new GeoApiContext.Builder()
    .apiKey("AIzaSyCghtDOMLtkbObzN2e0dz2KrYkLyd6yHrI")
    .build();
      ObjectMapper mapper = new ObjectMapper();
      GeocodingResult[] results = null;

      try {
              results = GeocodingApi.geocode(context,
                  city +","+ region).await();
      } catch (ApiException | InterruptedException | IOException ex) {
          Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
      }
      
      double lat = results[0].geometry.location.lat;
      double lng = results[0].geometry.location.lng;
      
      try {
          Gson gson = new GsonBuilder().setPrettyPrinting().create();
            //System.out.println(gson.toJson(results[0].addressComponents));
            List<AddressComponent> myObjects = mapper.readValue(gson.toJson(results[0].addressComponents), mapper.getTypeFactory().constructCollectionType(List.class, AddressComponent.class));
            for(AddressComponent element : myObjects ){
              if(element.types[0].toString().equalsIgnoreCase("ADMINISTRATIVE_AREA_LEVEL_1")){
                  region = element.shortName;
              }
          }
            
           for(AddressComponent element : myObjects ){
              if(element.types[0].toString().equalsIgnoreCase("LOCALITY")){
                  city = element.longName;
              }
          }
            
      } catch (IOException ex) {
          Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
          return "Something happened with that damn List<> code: " + ex.getMessage();
      }

      
PendingResult<TimeZone> timeZone = TimeZoneApi.getTimeZone(context, new LatLng(lat,lng));
      ZonedDateTime currentTime = null;
      try {
          currentTime = ZonedDateTime.now(timeZone.await().toZoneId());
      } catch (ApiException | InterruptedException | IOException ex) {
          Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
      }
      time = "The time in "
             + city
             + ", "
             + region
             + " is "
             + currentTime.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT));
      
return time;
  }
  @RequestMapping("/movie")
  @ResponseBody
  String movie(@RequestParam(value="title", required=true) String movie) {
      OmdbApi omdb = new OmdbApi("e351dd0");
      
      final Matcher matcherP = patternP.matcher(movie);
      final Matcher matcherM = patternM.matcher(movie);
      final Matcher matcherY = patternY.matcher(movie);
      final Matcher matcherC = patternCast.matcher(movie);

      try {
          if(matcherP.find()){
          return plot(omdb, matcherP, matcherY);
              }
          else if(matcherC.find()){
              return cast(omdb,matcherC, matcherY);
          }
          else
          {
              return scores(omdb,matcherM,matcherY);
          }

      } catch (OMDBException ex) {
          Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
          return ex.getResponse()+" And make sure you aren't typing as all one word.";
      }

  }
  
  String plot(OmdbApi omdb, Matcher matcherP, Matcher matcherY) throws OMDBException{
      OmdbVideoBasic result = null;
      String everything = "";
      String year="";
      String movieTitle = "";
      matcherP.reset();
      while(matcherP.find())
              movieTitle += matcherP.group(2);
          while (matcherY.find()) {
              year += matcherY.group(0);
                }
          
          if(year.isEmpty())
              return "You need the year for this. Use !movie " + movieTitle.trim()
                      + " to see what movies you might want to specify.";
      
      result = omdb.ShortTitleSearch(movieTitle.trim(),Integer.parseInt(year));
                  everything += result.getPlot();
      
      return everything;
  
  }
  
  public String cast(OmdbApi omdb, Matcher matcherC, Matcher matcherY) throws OMDBException{
      OmdbVideoFull result = null;
      String everything = "The cast of ";
      String year="";
      String movieTitle = "";
      matcherC.reset();
      while(matcherC.find())
              movieTitle += matcherC.group(2);
          while (matcherY.find()) {
              year += matcherY.group(0);
                }
      result = omdb.FullTitleSearch(movieTitle.trim(),Integer.parseInt(year));
                everything += result.getTitle()+" is ";
                  everything += result.getActors();
      
      return everything;
  
  }
  
  String scores(OmdbApi omdb,Matcher matcherM, Matcher matcherY) throws OMDBException{
      OmdbVideoFull result = null;
      SearchResults results = null;
      String everything = "";
      String year="";
      String movieTitle = "";
      while (matcherM.find()) {
    movieTitle += matcherM.group(0);
    }

    while (matcherY.find()) {
    year += matcherY.group(0);
    }
    
    if(!year.isEmpty())
          result = omdb.FullTitleSearch(movieTitle,Integer.parseInt(year));
          else{
              results = omdb.search(new OmdbBuilder().setSearchTerm(movieTitle).setTypeMovie().build());
              for(OmdbVideoBasic r : results.getResults()){
                  everything += r.getTitle()+", "+r.getYear()+"; ";
              }
              return "There are a few movies with similar titles: "+everything;
          }
    return "The movie "+result.getTitle()+" was released "+result.getReleased()+" and has a Metascore of "+result.getMetascore()+", and imDB rating of "+result.getImdbRating();
  }
  
  String temperature(String city, String report, String region) throws JAXBException, IOException{
      YahooWeatherService service = new YahooWeatherService();
          LimitDeclaration channel = service.getForecastForLocation(city+","+region, DegreeUnit.CELSIUS);
          List<Channel> list = channel.all();
          
          city = city.replace(region,"");
          final String finalCity = city.trim();
          list.removeIf((Channel chan) -> !chan.getLocation().getCity().equalsIgnoreCase(finalCity));
             report = "In "
                 + list.get(0).getLocation().getCity()
                 +", "
                 + list.get(0).getLocation().getRegion()
                 + ", the forecast is "
                 + list.get(0).getItem().getCondition().getText()
                 + " with a high of "
                 + list.get(0).getItem().getForecasts().get(0).getHigh()
                 + " and a low of "
                 + list.get(0).getItem().getForecasts().get(0).getLow()
                 + ". The temperature is "
                 + list.get(0).getItem().getCondition().getTemp()
                 + "° C";
             return report;
  }
  
  String forecast(Matcher matcherForecast, String city, String report, String region) throws JAXBException, IOException{
      matcherForecast.reset();
      SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy");
      String day="";
      while(matcherForecast.find()){
        day += matcherForecast.group(2);
      }
      city = city.replace(region,"");
      city = city.replace("-fc","");
      city = city.replace(day, "");
      final String finalDay = day;
      YahooWeatherService service = new YahooWeatherService();
      YahooWeatherService.LimitDeclaration channel = service.getForecastForLocation(city+","+region, DegreeUnit.CELSIUS);
      List<Channel> list = channel.all();
      final String finalCity = city.trim();
      list.removeIf((Channel chan) -> !chan.getLocation().getCity().equalsIgnoreCase(finalCity));
      List<Forecast> lst = list.get(0).getItem().getForecasts();
      lst.removeIf((Forecast f) -> !f.getDay().toString().equalsIgnoreCase(finalDay));
      
      report = "The forecast for "
              + dateFormat.format(lst.get(0).getDate())
              + " in "
              + list.get(0).getLocation().getCity() + ", " + list.get(0).getLocation().getRegion()
              + " is "
              + lst.get(0).getText()
              + " with a high of "
              + lst.get(0).getHigh()
              + "° and a low of "
              + lst.get(0).getLow()
              + "°";
      
      return report;
  }

  @RequestMapping("/tomatoes")
  @ResponseBody
  String RottenTomatoes(@RequestParam(value="title", required=true) String movie){
      String response = "";
      return response;
  }

}