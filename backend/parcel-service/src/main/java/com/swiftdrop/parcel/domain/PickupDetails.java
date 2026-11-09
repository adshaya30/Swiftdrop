package com.swiftdrop.parcel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** BR-09: contact/address and district snapshot embedded in the parcel. */
@Embeddable
public class PickupDetails {
    @Column(name = "pickup_contact_name", length = 100, nullable = false, updatable = false)
    private String contactName;

    @Column(name = "pickup_contact_phone", length = 16, nullable = false, updatable = false)
    private String contactPhone;

    @Column(name = "pickup_address_line1", length = 150, nullable = false, updatable = false)
    private String addressLine1;

    @Column(name = "pickup_address_line2", length = 150, nullable = true, updatable = false)
    private String addressLine2;

    @Column(name = "pickup_city", length = 80, nullable = false, updatable = false)
    private String city;

    @Column(name = "pickup_district_code", length = 20, nullable = false, updatable = false)
    private String districtCode;

    @Column(name = "pickup_district_name", length = 40, nullable = false, updatable = false)
    private String districtName;

    @Column(name = "pickup_postal_code", length = 5, nullable = true, updatable = false)
    private String postalCode;

    protected PickupDetails() { }

    public PickupDetails(String contactName, String contactPhone, String addressLine1, String addressLine2, String city, String districtCode, String districtName, String postalCode) {
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.districtCode = districtCode;
        this.districtName = districtName;
        this.postalCode = postalCode;
    }

    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getDistrictCode() { return districtCode; }
    public String getDistrictName() { return districtName; }
    public String getPostalCode() { return postalCode; }
}
