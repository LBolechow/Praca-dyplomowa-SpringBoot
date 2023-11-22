package pl.lukbol.dyplom.controllers;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import pl.lukbol.dyplom.classes.Material;
import pl.lukbol.dyplom.classes.Order;
import pl.lukbol.dyplom.classes.User;
import pl.lukbol.dyplom.repositories.MaterialRepository;
import pl.lukbol.dyplom.repositories.OrderRepository;
import pl.lukbol.dyplom.repositories.UserRepository;
import pl.lukbol.dyplom.utilities.AuthenticationUtils;
import pl.lukbol.dyplom.utilities.DateUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;

@Controller
public class OrderController {
    private UserRepository userRepository;

    private OrderRepository orderRepository;

    private MaterialRepository materialRepository;


    public OrderController(UserRepository userRepository, OrderRepository orderRepository, MaterialRepository materialRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.materialRepository = materialRepository;
    }

        @GetMapping("/currentDate")
        public Map<String, Object> getCurrentDate() {
            Map<String, Object> response = new HashMap<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            Date currentDate = new Date();
            String formattedDate = sdf.format(currentDate);

            response.put("currentDate", formattedDate);

            return response;
        }

    @GetMapping("/checkAvailability")
    public ResponseEntity<Map<String, Object>> checkAvailability(@RequestParam double durationHours) {
        try {
            // Przygotowanie odpowiedzi
            Map<String, Object> response = new HashMap<>();

            // Pobranie daty i godziny bieżącej
            Calendar currentDateTime = Calendar.getInstance();
            currentDateTime.set(Calendar.HOUR_OF_DAY, 8); // Ustawienie na początek dnia
            currentDateTime.set(Calendar.MINUTE, 0);
            currentDateTime.set(Calendar.SECOND, 0);

            // Iteracyjne sprawdzanie dostępności w ramach dni roboczych
            while (currentDateTime.get(Calendar.HOUR_OF_DAY) < 16) {
                // Sprawdzanie dostępności dla danego dnia
                if (isWorkingDay(currentDateTime)) {
                    // Obliczanie daty i godziny zakończenia na podstawie czasu trwania
                    Calendar endDateTime = (Calendar) currentDateTime.clone();
                    int durationMinutes = (int) (durationHours * 60);
                    endDateTime.add(Calendar.MINUTE, durationMinutes);

                    // Sprawdzanie dostępności pracowników
                    List<User> availableUsers = findAvailableUsers(currentDateTime.getTime(), endDateTime.getTime());

                    // Propozycja pierwszego dostępnego pracownika
                    if (!availableUsers.isEmpty()) {
                        User suggestedUser = availableUsers.get(0);

                        // Ustawienie danych w odpowiedzi
                        response.put("status", "success");
                        response.put("startDate", formatDate(currentDateTime.getTime()));
                        response.put("startTime", formatTime(currentDateTime.getTime()));
                        response.put("endDate", formatDate(endDateTime.getTime()));
                        response.put("endTime", formatTime(endDateTime.getTime()));
                        response.put("suggestedUser", suggestedUser.getName());

                        response.put("durationMinutes", durationMinutes);

                        return new ResponseEntity<>(response, HttpStatus.OK);
                    }
                }

                // Przejście do następnego dnia
                currentDateTime.add(Calendar.DAY_OF_MONTH, 1);
                currentDateTime.set(Calendar.HOUR_OF_DAY, 8); // Ustawienie na początek dnia
            }

            response.put("status", "error");
            response.put("message", "Brak dostępnych pracowników w ramach dni roboczych.");

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(date);
    }


    private String formatTime(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(date);
    }


        private boolean isWorkingDay(Calendar calendar) {
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            return dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY;
        }


        private List<User> findAvailableUsers(Date startDate, Date endDate) {
            // Pobranie wszystkich zamówień w danym przedziale czasowym
            List<Order> conflictingOrders = orderRepository.findByStartDateBetweenOrEndDateBetween(startDate, endDate, startDate, endDate);

            // Pobranie wszystkich pracowników
            List<User> allUsers = userRepository.findAll();

            // Odfiltrowanie dostępnych pracowników
            List<User> availableUsers = new ArrayList<>(allUsers);

            for (Order order : conflictingOrders) {
                availableUsers.remove(order.getUser());
            }

            return availableUsers;
        }

    @GetMapping(value ="/order/getAllOrders")
    public ResponseEntity<List<Order>> getAllOrders() {
        try {
            List<Order> orders = orderRepository.findAll();

            return new ResponseEntity<>(orders, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @GetMapping(value = "/order/getDailyOrders")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getDailyOrders(
            @RequestParam(name = "selectedDate", required = false) String selectedDateStr) {
        try {
            LocalDate selectedDate;

            if (selectedDateStr != null && !selectedDateStr.isEmpty()) {
                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendOptional(DateTimeFormatter.ofPattern("dd.MM.yyYY"))
                        .appendOptional(DateTimeFormatter.ofPattern("d.MM.yy"))
                        .appendOptional(DateTimeFormatter.ofPattern("dd.M.yy"))
                        .appendOptional(DateTimeFormatter.ofPattern("d.M.yy"))
                        .toFormatter();
                selectedDate = LocalDate.parse(selectedDateStr, formatter);
            } else {
                selectedDate = LocalDate.now();
            }

            LocalDateTime startOfDay = selectedDate.atStartOfDay();
            LocalDateTime endOfDay = selectedDate.atTime(23, 59, 59);

            List<Order> orders = orderRepository.findByEndDateBetween(
                    Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant()),
                    Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant())
            );

            List<Map<String, Object>> ordersData = new ArrayList<>();

            for (Order order : orders) {
                Map<String, Object> orderData = new HashMap<>();
                orderData.put("id", order.getId());
                orderData.put("description", order.getDescription());
                orderData.put("clientName", order.getClientName());
                orderData.put("employeeName", order.getEmployeeName());
                orderData.put("status", order.isStatus()); // true = w trakcie, false = zakończone
                orderData.put("startDate", order.getStartDate());
                orderData.put("endDate", order.getEndDate()); // dodaj endDate do danych
                ordersData.add(orderData);
            }

            return new ResponseEntity<>(ordersData, HttpStatus.OK);
        } catch (DateTimeParseException e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Błąd parsowania daty");
            errorResponse.put("message", e.getMessage());
            return new ResponseEntity<>(Collections.singletonList(errorResponse), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Błąd podczas przetwarzania żądania");
            errorResponse.put("message", e.getMessage());
            return new ResponseEntity<>(Collections.singletonList(errorResponse), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @PostMapping(value = "/order/add", consumes = {"application/json"})
    @ResponseBody
    public void addOrder(Authentication authentication, @RequestBody Map<String, Object> request) {
        String selectedUser = (String) request.get("selectedUser");
        List<User> usr = userRepository.findByNameContainingIgnoreCase(selectedUser);
        User user = usr.get(0);
        Date convertedDate = DateUtils.currentDate();

        String description = (String) request.get("description");
        String clientName = (String) request.get("clientName");
        String email = (String) request.get("email");
        String phoneNumber = (String) request.get("phoneNumber");

        String startDateString = (String) request.get("startDate");
        Date startDate = parseDateString(startDateString);


        String endDateString = (String) request.get("endDate");
        Date endDate = parseDateString(endDateString);


        String hour = (String) request.get("hours");
        double hours =  Double.parseDouble(hour);

        List<String> items = (List<String>) request.get("items");



        int price = Integer.parseInt((String) request.get("price"));


        Order newOrder = new Order(description, clientName, email, phoneNumber, usr.get(0).getName(), startDate, endDate, true, price, hours, null, user);

        List<Material> materials = new ArrayList<>();
        for (String item : items) {
            Material material = new Material(item, newOrder, false);
            materials.add(material);
        }
        newOrder.setMaterials(materials);

        orderRepository.save(newOrder);
    }
    @GetMapping("/order/getOrderDetails/{id}")
    public ResponseEntity<Map<String, Object>> getOrderDetails(@PathVariable Long id) {
        try {
            Optional<Order> optionalOrder = orderRepository.findById(id);

            if (optionalOrder.isPresent()) {
                Order order = optionalOrder.get();

                Map<String, Object> orderDetails = new HashMap<>();
                orderDetails.put("id", order.getId());
                orderDetails.put("description", order.getDescription());
                orderDetails.put("clientName", order.getClientName());
                orderDetails.put("email", order.getClientEmail());
                orderDetails.put("phoneNumber", order.getPhoneNumber());
                orderDetails.put("startDate", formatDate(order.getStartDate()));
                orderDetails.put("startTime", formatTime(order.getStartDate()));
                orderDetails.put("endDate", formatDate(order.getEndDate()));
                orderDetails.put("endTime", formatTime(order.getEndDate()));
                orderDetails.put("employee", order.getUser().getName());
                orderDetails.put("hours", order.getDuration());
                orderDetails.put("price", order.getPrice());
                orderDetails.put("materials", order.getMaterials());
                orderDetails.put("status", order.isStatus());


                return new ResponseEntity<>(orderDetails, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    private Date parseDateString(String dateString) {
        Date date = null;
        try {
            date = DateUtils.parseDate(dateString, "yyyy-MM-dd HH:mm:ss");
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date;
    }
    @Transactional
    @PostMapping(value = "/order/edit/{id}", consumes = {"application/json"})
    @ResponseBody
    public void editOrder(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Optional<Order> optionalOrder = orderRepository.findById(id);

        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            materialRepository.deleteAllByOrder(order);

            String selectedUser = (String) request.get("selectedUser");
            List<User> users = userRepository.findByNameContainingIgnoreCase(selectedUser);
            User user = users.isEmpty() ? null : users.get(0);

            order.setDescription((String) request.get("description"));
            order.setClientName((String) request.get("clientName"));
            order.setClientEmail((String) request.get("email"));
            order.setPhoneNumber((String) request.get("phoneNumber"));

            String startDateString = (String) request.get("startDate");
            Date startDate = parseDateString(startDateString);
            order.setStartDate(startDate);

            String endDateString = (String) request.get("endDate");
            Date endDate = parseDateString(endDateString);
            order.setEndDate(endDate);

            double hours = Double.parseDouble((String) request.get("hours"));
            order.setDuration(hours);

            int price = Integer.parseInt((String) request.get("price"));
            order.setPrice(price);

            Boolean status = (Boolean) request.get("status");
            order.setStatus(status);

            List<String> items = (List<String>) request.get("items");
            List<Material> newMaterials = new ArrayList<>();
            for (String item : items) {
                Material material = new Material(item, order, false);
                newMaterials.add(material);
            }
            order.setMaterials(newMaterials);

            orderRepository.save(order);
        }
    }
}
