package iecd.a51597.common.store;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Data Transfer Object for User information.
 * 
 * @param userId unique user identifier
 * @param username user's unique login name
 * @param photo base64 encoded photo (nullable)
 * @param nationality user's nationality (nullable)
 * @param dob user's date of birth (nullable)
 * @param stats user's game statistics (nullable)
 */
public record UserDTO(
        UUID userId,
        String username,
        String photo, // can be null
        String nationality, // can be null
        LocalDate dob, // can be null
        PlayerStats stats, // can be null
        boolean online,
        String favoriteColor // can be null
) {
    /**
     * Calculates the user's age based on date of birth.
     * @return age in years
     */
    public int getAge() {
        return dob.until(LocalDate.now()).getYears();
    }
}
