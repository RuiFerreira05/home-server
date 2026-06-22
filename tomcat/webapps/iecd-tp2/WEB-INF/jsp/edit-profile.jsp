<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Standard request scope setting using plain Java scriptlets
    request.setAttribute("pageStyle", "profile.css");
    request.setAttribute("pageActive", "profile");

    // Parse country_codes.xml dynamically from the classpath
    java.util.List<String[]> countries = new java.util.ArrayList<>();
    try {
        java.io.InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("country_codes.xml");
        if (is == null) {
            is = getClass().getResourceAsStream("/country_codes.xml");
        }
        if (is == null) {
            is = application.getResourceAsStream("/WEB-INF/classes/country_codes.xml");
        }
        if (is != null) {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(is);
            org.w3c.dom.NodeList nodes = doc.getElementsByTagName("country");
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);
                String code = el.getAttribute("code");
                String name = el.getTextContent();
                if (code != null && name != null) {
                    countries.add(new String[]{code.trim().toUpperCase(), name.trim()});
                }
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    iecd.a51597.common.store.UserDTO userObj = (iecd.a51597.common.store.UserDTO) session.getAttribute("user");
    
    if (userObj == null) {
        response.sendRedirect(request.getContextPath() + "/auth/login");
        return;
    }

    String error = (String) request.getAttribute("error");
    String photo = userObj.photo();
    String username = userObj.username();
    String nationality = userObj.nationality() != null ? userObj.nationality() : "";
    String dobStr = userObj.dob() != null ? userObj.dob().toString() : "";
    String favoriteColor = userObj.favoriteColor() != null ? userObj.favoriteColor() : "#0b0c13";
    
    String contextPath = request.getContextPath();
%>
<jsp:include page="common/header.jsp"/>

<div class="profile-wrapper">
    
    <!-- Top Action Headers -->
    <div class="profile-header-actions">
        <a href="<%= contextPath %>/profile" class="btn btn-outline btn-sm">
            <i class="fa-solid fa-arrow-left"></i> Back to Profile
        </a>
    </div>

    <div class="edit-profile-container">
        <div class="card edit-profile-card">
            <div class="card-header">
                <h2 class="card-title"><i class="fa-solid fa-user-pen"></i> Update Profile Information</h2>
                <p class="card-subtitle">Modify your details or set a customized profile photo avatar</p>
            </div>
            
            <div class="card-body">
                <!-- Success/Error Alerts -->
                <% if (error != null) { %>
                    <div class="alert alert-danger" style="margin-bottom: 1.5rem;">
                        <i class="fa-solid fa-circle-exclamation alert-icon"></i>
                        <div class="alert-content">
                            <span class="alert-title">Update Failed</span>
                            <span class="alert-text"><%= error %></span>
                        </div>
                    </div>
                <% } %>

                <form action="<%= contextPath %>/profile/edit" method="POST" enctype="multipart/form-data" class="edit-profile-form">
                    
                    <div class="form-grid">
                        <!-- Left Block: Form Inputs -->
                        <div class="form-inputs-block">
                            <div class="form-group">
                                <label for="username" class="form-label">Username</label>
                                <div class="input-wrapper">
                                    <i class="fa-solid fa-user input-icon"></i>
                                    <input type="text" id="username" name="username" class="form-control" value="<%= username %>" placeholder="Choose a new username" required minlength="3">
                                </div>
                            </div>

                            <div class="form-group">
                                <label for="password" class="form-label">New Password (Optional)</label>
                                <div class="input-wrapper">
                                    <i class="fa-solid fa-lock input-icon"></i>
                                    <input type="password" id="password" name="password" class="form-control" placeholder="Leave empty to keep unchanged" minlength="4">
                                </div>
                            </div>

                            <div class="form-group" style="position: relative;">
                                <label for="nationality-search" class="form-label">Nationality</label>
                                <div class="input-wrapper" id="nationality-wrapper">
                                    <i class="fa-solid fa-earth-americas input-icon"></i>
                                    <input type="text" id="nationality-search" class="form-control" placeholder="Search and select country..." autocomplete="off" style="padding-right: 3rem;">
                                    <input type="hidden" id="nationality" name="nationality" value="<%= nationality %>">
                                    <span id="selected-flag-preview" class="flag-preview-badge" style="position: absolute; right: 1rem; display: none; align-items: center; pointer-events: none;"></span>
                                </div>
                                
                                <!-- Custom Dropdown Overlay -->
                                <div id="nationality-dropdown" class="autocomplete-dropdown" style="display: none; position: absolute; top: 100%; left: 0; right: 0; z-index: 1000;">
                                    <div id="nationality-results-container" class="results-container">
                                        <!-- populated dynamically -->
                                    </div>
                                </div>
                            </div>

                            <div class="form-group">
                                <label for="dob" class="form-label">Date of Birth</label>
                                <div class="input-wrapper">
                                    <i class="fa-solid fa-calendar-days input-icon"></i>
                                    <input type="date" id="dob" name="dob" class="form-control" value="<%= dobStr %>">
                                </div>
                            </div>

                            <div class="form-group">
                                <label for="favoriteColor" class="form-label">Preferred Game Background Color</label>
                                <div class="input-wrapper" style="display: flex; align-items: center; gap: 10px;">
                                    <i class="fa-solid fa-palette input-icon" style="position: static; padding: 0 5px; color: var(--text-muted);"></i>
                                    <input type="color" id="favoriteColor" name="favoriteColor" class="form-control" value="<%= favoriteColor %>" style="width: 70px; height: 38px; padding: 2px; border-radius: 6px; cursor: pointer; border: 1px solid var(--border-color); background: none;">
                                </div>
                            </div>
                        </div>

                        <!-- Right Block: Photo Upload -->
                        <div class="form-photo-block">
                            <label class="form-label">Profile Avatar</label>
                            
                            <div class="photo-preview-container">
                                <% if (photo != null && !photo.isEmpty()) { %>
                                    <img id="photo-preview" src="<%= contextPath %>/photo/<%= photo %>" alt="Current avatar" class="avatar-edit-preview">
                                <% } else { %>
                                    <div id="photo-placeholder" class="avatar-edit-preview placeholder-icon">
                                        <i class="fa-solid fa-user"></i>
                                    </div>
                                    <img id="photo-preview" src="" alt="Preview" class="avatar-edit-preview" style="display: none;">
                                <% } %>
                            </div>

                            <div class="file-upload-wrapper">
                                <label for="photo-file" class="btn btn-outline btn-block file-upload-btn">
                                    <i class="fa-solid fa-upload"></i> Choose Photo File
                                </label>
                                <input type="file" id="photo-file" name="photo" accept="image/png, image/jpeg, image/webp" class="file-input" onchange="previewFile()">
                                <span class="file-info-label" id="file-info-label">No file selected</span>
                            </div>
                        </div>
                    </div>

                    <hr class="card-divider" style="margin: 2rem 0;">

                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary btn-block">
                            <i class="fa-solid fa-floppy-disk"></i> Save Profile Changes
                        </button>
                    </div>

                </form>
            </div>
        </div>
    </div>
</div>

<script>
    const allCountries = [
        <% for (int i = 0; i < countries.size(); i++) { 
            String[] c = countries.get(i);
        %>
            { code: "<%= c[0] %>", name: "<%= c[1].replace("\"", "\\\"") %>" }<%= i < countries.size() - 1 ? "," : "" %>
        <% } %>
    ];

    function previewFile() {
        var preview = document.getElementById('photo-preview');
        var placeholder = document.getElementById('photo-placeholder');
        var fileInput = document.getElementById('photo-file');
        var label = document.getElementById('file-info-label');
        var file = fileInput.files[0];

        if (file) {
            label.textContent = file.name + ' (' + Math.round(file.size / 1024) + ' KB)';
            
            var reader = new FileReader();
            reader.onloadend = function () {
                preview.src = reader.result;
                preview.style.display = 'block';
                if (placeholder) {
                    placeholder.style.display = 'none';
                }
            }
            reader.readAsDataURL(file);
        } else {
            label.textContent = 'No file selected';
        }
    }

    document.addEventListener("DOMContentLoaded", function() {
        const searchInput = document.getElementById("nationality-search");
        const hiddenInput = document.getElementById("nationality");
        const dropdown = document.getElementById("nationality-dropdown");
        const resultsContainer = document.getElementById("nationality-results-container");
        const flagPreview = document.getElementById("selected-flag-preview");
        
        // Find initial country name if code exists
        const initialCode = hiddenInput.value;
        if (initialCode) {
            const initialCountry = allCountries.find(c => c.code === initialCode);
            if (initialCountry) {
                searchInput.value = initialCountry.name + " (" + initialCode + ")";
                updateFlagPreview(initialCode);
            }
        }
        
        function updateFlagPreview(code) {
            if (code) {
                flagPreview.innerHTML = '<img src="https://flagcdn.com/16x12/' + code.toLowerCase() + '.png" alt="flag" style="border-radius: 1px; box-shadow: 0 1px 2px rgba(0,0,0,0.3);">';
                flagPreview.style.display = "flex";
            } else {
                flagPreview.innerHTML = "";
                flagPreview.style.display = "none";
            }
        }
        
        function renderItems(filterText) {
            resultsContainer.innerHTML = "";
            const query = filterText.toLowerCase().trim();
            const filtered = allCountries.filter(c => 
                c.name.toLowerCase().includes(query) || c.code.toLowerCase().includes(query)
            );
            
            if (filtered.length === 0) {
                resultsContainer.innerHTML = `<div class="dropdown-loader">No matching countries found</div>`;
                return;
            }
            
            filtered.forEach(c => {
                const item = document.createElement("div");
                item.className = "dropdown-item";
                item.style.cursor = "pointer";
                item.style.padding = "0.7rem 1rem";
                
                item.innerHTML = 
                    '<div style="display: flex; align-items: center; gap: 8px;">' +
                        '<img src="https://flagcdn.com/16x12/' + c.code.toLowerCase() + '.png" alt="flag" style="border-radius: 1px; box-shadow: 0 1px 2px rgba(0,0,0,0.2);">' +
                        '<span style="font-weight: 500; font-size: 0.9rem; color: var(--text-main);">' + c.name + '</span>' +
                        '<span style="font-size: 0.75rem; color: var(--text-muted);">(' + c.code + ')</span>' +
                    '</div>';
                
                item.onclick = function() {
                    searchInput.value = c.name + " (" + c.code + ")";
                    hiddenInput.value = c.code;
                    updateFlagPreview(c.code);
                    dropdown.style.display = "none";
                };
                
                resultsContainer.appendChild(item);
            });
        }
        
        searchInput.addEventListener("focus", function() {
            dropdown.style.display = "block";
            renderItems(searchInput.value.includes("(") ? "" : searchInput.value);
        });
        
        searchInput.addEventListener("input", function() {
            dropdown.style.display = "block";
            // If they are editing, clear the hidden code
            hiddenInput.value = "";
            updateFlagPreview("");
            renderItems(searchInput.value);
        });
        
        // Hide when clicking outside
        document.addEventListener("click", function(e) {
            if (!e.target.closest("#nationality-wrapper") && !e.target.closest("#nationality-dropdown")) {
                dropdown.style.display = "none";
                
                // If they clicked away and didn't select, but had an initial value, restore it
                if (!hiddenInput.value && initialCode) {
                    const initialCountry = allCountries.find(c => c.code === initialCode);
                    if (initialCountry) {
                        searchInput.value = initialCountry.name + " (" + initialCode + ")";
                        hiddenInput.value = initialCode;
                        updateFlagPreview(initialCode);
                    }
                } else if (!hiddenInput.value) {
                    searchInput.value = "";
                    updateFlagPreview("");
                }
            }
        });
    });
</script>

<jsp:include page="common/footer.jsp"/>
