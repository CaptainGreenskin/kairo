/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.examples.demo;

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.ToolHandler;
import java.util.Map;

/**
 * A mock weather lookup tool that returns simulated weather data for known cities.
 *
 * <p>This tool demonstrates how to create a custom read-only tool using the Kairo tool annotation
 * pattern. It maintains a static map of city-to-weather data and returns a friendly weather report
 * for each supported city.
 *
 * <p>Supported cities: Beijing, Shanghai, Tokyo, New York, London, Paris, Sydney.
 */
@Tool(
        name = "weather",
        description = "Look up current weather for a city. Returns temperature and conditions.",
        category = ToolCategory.INFORMATION)
public class WeatherTool implements ToolHandler {

    /** Mock weather data keyed by lowercase city name. */
    private static final Map<String, String> WEATHER_DATA =
            Map.of(
                    "beijing", "Beijing: 22°C, Sunny",
                    "shanghai", "Shanghai: 26°C, Partly Cloudy",
                    "tokyo", "Tokyo: 18°C, Rainy",
                    "new york", "New York: 15°C, Overcast",
                    "london", "London: 12°C, Foggy",
                    "paris", "Paris: 20°C, Clear",
                    "sydney", "Sydney: 28°C, Sunny");

    @ToolParam(description = "The city name to look up weather for", required = true)
    private String city;

    /**
     * Execute the weather lookup for the given city.
     *
     * @param input the input parameters; must contain a "city" key
     * @return a {@link ToolResult} with the weather report, or an error if the city is unknown
     */
    @Override
    public ToolResult execute(Map<String, Object> input) {
        String cityName = (String) input.get("city");
        if (cityName == null || cityName.isBlank()) {
            return new ToolResult("weather", "Parameter 'city' is required", true, Map.of());
        }

        String weather = WEATHER_DATA.get(cityName.toLowerCase().trim());
        if (weather == null) {
            return new ToolResult(
                    "weather",
                    "Unknown city: "
                            + cityName
                            + ". Supported cities: "
                            + String.join(
                                    ", ",
                                    "Beijing",
                                    "Shanghai",
                                    "Tokyo",
                                    "New York",
                                    "London",
                                    "Paris",
                                    "Sydney"),
                    false,
                    Map.of("city", cityName));
        }

        return new ToolResult("weather", weather, false, Map.of("city", cityName));
    }
}
