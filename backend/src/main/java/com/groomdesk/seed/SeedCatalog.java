package com.groomdesk.seed;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Static demo data (fictional businesses and people) plus a deterministic name/phone source. */
final class SeedCatalog {

    private SeedCatalog() {
    }

    record FranchiseSpec(String name, String ownerName, String gstin, BigDecimal royaltyPercent, int foundedYear) {
    }

    /** weekdayMean = typical weekday bill count; spreads outlets so the HQ ranking is interesting. */
    record OutletSpec(String code, String name, String area, String city, int franchise, double weekdayMean) {
    }

    static final List<FranchiseSpec> FRANCHISES = List.of(
            new FranchiseSpec("Coromandel Grooming Pvt Ltd", "Senthil Kumar", "33AAFCC4821K1Z6", new BigDecimal("8.00"), 2019),
            new FranchiseSpec("Marina Men's Care LLP", "Arjun Raghavan", "33AAPFM7314L1Z2", new BigDecimal("7.50"), 2020),
            new FranchiseSpec("Southline Salons", "Faizal Ahmed", "33ABCFS2096Q1Z9", new BigDecimal("6.00"), 2022),
            new FranchiseSpec("Kongu Grooming Partners", "Prakash Palanisamy", "33AAKFK5530R1Z4", new BigDecimal("10.00"), 2021)
    );

    static final List<OutletSpec> OUTLETS = List.of(
            new OutletSpec("TNR", "T. Nagar", "Usman Road", "Chennai", 0, 54),
            new OutletSpec("ANN", "Anna Nagar", "2nd Avenue", "Chennai", 0, 50),
            new OutletSpec("MNP", "Manapakkam", "Mount Poonamallee Road", "Chennai", 0, 36),
            new OutletSpec("VLY", "Velachery", "100 Feet Road", "Chennai", 1, 46),
            new OutletSpec("ADY", "Adyar", "LB Road", "Chennai", 1, 40),
            new OutletSpec("MYL", "Mylapore", "Luz Church Road", "Chennai", 1, 34),
            new OutletSpec("OMR", "Thoraipakkam", "OMR", "Chennai", 2, 44),
            new OutletSpec("TBM", "Tambaram", "GST Road", "Chennai", 2, 29),
            new OutletSpec("CBE", "RS Puram", "DB Road", "Coimbatore", 3, 32),
            new OutletSpec("MDU", "KK Nagar", "80 Feet Road", "Madurai", 3, 27)
    );

    private record Community(double weight, List<String> first, List<String> last, double initialStyle) {
    }

    private static final List<Community> COMMUNITIES = List.of(
            new Community(75, List.of(
                    "Karthik", "Vignesh", "Arun", "Prakash", "Suresh", "Ramesh", "Senthil", "Murugan", "Balaji", "Dinesh",
                    "Gokul", "Hari", "Manoj", "Naveen", "Praveen", "Rajesh", "Sathish", "Saravanan", "Sundar", "Vijay",
                    "Ajith", "Surya", "Ashwin", "Aravind", "Bharath", "Deepak", "Ganesh", "Kishore", "Lokesh", "Madhan",
                    "Nithin", "Pradeep", "Raghav", "Sanjay", "Siddharth", "Sriram", "Udhay", "Venkat", "Yuvan", "Vasanth",
                    "Kavin", "Pranav", "Akash", "Abishek", "Harish", "Kumaran", "Muthu", "Prabhu", "Ravi", "Selvam",
                    "Elango", "Shankar", "Mohan", "Anand", "Gautham", "Jeeva", "Karthikeyan", "Naresh", "Santhosh", "Varun",
                    "Dhanush", "Sabari", "Tamilselvan", "Logesh", "Vetri", "Aadhav", "Kathir", "Inban", "Bala", "Thiru"),
                    List.of("Kumar", "Raj", "Rajan", "Krishnan", "Subramanian", "Natarajan", "Ramasamy", "Selvaraj",
                            "Shanmugam", "Venkatesan", "Balasubramanian", "Srinivasan", "Raghavan", "Govindan", "Sekar",
                            "Babu", "Anand", "Mani", "Ganesan", "Arumugam", "Kannan", "Durai", "Pandian", "Ravichandran",
                            "Elangovan", "Sundaram", "Murugesan", "Palanisamy", "Jayaraman", "Chandrasekar"),
                    0.35),
            new Community(10, List.of("Mohammed", "Imran", "Faizal", "Riyaz", "Sameer", "Asif", "Arshad", "Irfan",
                    "Salman", "Junaid", "Abdul", "Rizwan", "Yusuf", "Nawaz"),
                    List.of("Khan", "Basha", "Ahmed", "Sheriff", "Rahman", "Hussain", "Ali", "Ibrahim"), 0.0),
            new Community(10, List.of("Joseph", "Antony", "Jerome", "Wilson", "Daniel", "Samuel", "Prince", "Jebin",
                    "Stephen", "Christopher", "Felix", "Augustine", "Paul", "Richard"),
                    List.of("Fernando", "D'Souza", "Thomas", "George", "Xavier", "Raj", "Arokiasamy", "Jebaraj"), 0.15),
            new Community(5, List.of("Rahul", "Amit", "Vikram", "Nikhil", "Aditya", "Rohan", "Kunal", "Ankit", "Varun", "Saurabh"),
                    List.of("Sharma", "Jain", "Gupta", "Agarwal", "Mehta", "Singh", "Verma", "Kothari"), 0.0)
    );

    private static final double[] COMMUNITY_WEIGHTS = COMMUNITIES.stream().mapToDouble(Community::weight).toArray();
    private static final String INITIALS = "ABDGJKMNPRSTV";

    private static final List<String> NOTES = List.of(
            "Prefers scissor cut, no machine on top",
            "Sensitive skin - use aloe gel after shave",
            "Keeps beard at 5mm, sharp neckline",
            "Allergic to strong fragrances",
            "Usually comes Sunday mornings",
            "Low fade on sides, longer on top",
            "Asks for head massage with every visit",
            "Hair colour: natural black, ammonia-free",
            "Brings son for kids haircut",
            "Short on time - keep it quick",
            "Likes a hot towel finish",
            "Avoid alcohol-based aftershave"
    );

    /** Deterministic source of names, unique valid Indian mobile numbers and notes. */
    static final class People {

        private static final char[] FIRST_DIGITS = {'9', '8', '7', '6'};
        private static final double[] FIRST_DIGIT_WEIGHTS = {40, 25, 20, 15};

        private final Random rnd;
        private final Set<String> usedPhones = new HashSet<>();

        People(Random rnd) {
            this.rnd = rnd;
        }

        /** 10 digits, starts with 6-9, unique across the whole seed. */
        String phone() {
            while (true) {
                char first = FIRST_DIGITS[BillHistoryGenerator.pickIndex(rnd, FIRST_DIGIT_WEIGHTS)];
                String phone = first + "%09d".formatted(rnd.nextInt(1_000_000_000));
                if (usedPhones.add(phone)) {
                    return phone;
                }
            }
        }

        String fullName() {
            Community c = COMMUNITIES.get(BillHistoryGenerator.pickIndex(rnd, COMMUNITY_WEIGHTS));
            String first = pick(c.first());
            if (rnd.nextDouble() < c.initialStyle()) {
                return first + " " + INITIALS.charAt(rnd.nextInt(INITIALS.length()));
            }
            return first + " " + pick(c.last());
        }

        /** Barbers are usually known by first name plus an initial. */
        String barberName() {
            Community c = COMMUNITIES.get(BillHistoryGenerator.pickIndex(rnd, COMMUNITY_WEIGHTS));
            return pick(c.first()) + " " + INITIALS.charAt(rnd.nextInt(INITIALS.length()));
        }

        String note() {
            return pick(NOTES);
        }

        private String pick(List<String> values) {
            return values.get(rnd.nextInt(values.size()));
        }
    }
}
