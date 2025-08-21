<?php
session_start();
if (!isset($_SESSION['user_id'])) {
    header('Location: login.php');
    exit;
}

require_once 'db_connect.php';

// JWT setup
require_once 'vendor/autoload.php'; // Make sure this path is correct for your setup
use Firebase\JWT\JWT;

$user_name = htmlspecialchars($_SESSION['user_name']);
$user_role = htmlspecialchars($_SESSION['user_role']);

// Check if user has an open session (not checked out)
$stmt = $pdo->prepare('SELECT * FROM user_sessions WHERE user_id = ? AND logout_time IS NULL ORDER BY login_time DESC LIMIT 1');
$stmt->execute([$_SESSION['user_id']]);
$session = $stmt->fetch();
$is_checked_in = $session ? true : false;

// Generate JWT token for deep link
$jwt_secret = 'test_secret_key_1234567890'; // Replace with a secure key and store safely
$issuedAt = time();
$expire = $issuedAt + 120; // 2 minutes
$session_id = session_id();
$payload = [
    'sub' => $_SESSION['user_id'],
    'iat' => $issuedAt,
    'exp' => $expire,
    'session_id' => $session_id
];
$jwt_token = JWT::encode($payload, $jwt_secret, 'HS256');

// Deep link with session and JWT token
$deep_link = "tracked://auth/login?session_id=" . urlencode($session_id) . "&token=" . urlencode($jwt_token);
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Dashboard</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f4f4f4; }
        .container { max-width: 500px; margin: 60px auto; background: #fff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); text-align: center; }
        a.logout { display: inline-block; margin-top: 20px; padding: 10px 20px; background: #dc3545; color: #fff; border-radius: 4px; text-decoration: none; }
        a.logout:hover { background: #a71d2a; }
        .action-btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 180px;
            height: 60px;
            margin: 20px 10px 0 10px;
            font-size: 18px;
            font-weight: bold;
            background: #007bff;
            color: #fff;
            border: none;
            border-radius: 8px;
            text-decoration: none;
            transition: background 0.2s;
            box-shadow: 0 1px 4px rgba(0,0,0,0.08);
        }
        .action-btn svg { margin-right: 10px; }
        .action-btn:hover { background: #0056b3; }
        #location-debug { margin: 20px auto 0 auto; max-width: 500px; background: #f8f9fa; border: 1px solid #ccc; border-radius: 8px; padding: 12px; color: #333; font-size: 15px; text-align: left; }
        .button { display: inline-block; padding: 10px 20px; background: #1976D2; color: #fff; border-radius: 5px; text-decoration: none; border: none; cursor: pointer; font-size: 16px; }
    </style>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
</head>
<body>
<div class="container">
    <h2>Welcome, <?php echo $user_name; ?>!</h2>
    <p>Your role: <strong><?php echo ucfirst($user_role); ?></strong></p>
    <div>
        <a class="action-btn" href="checkin.php">
            <!-- Location Pin SVG -->
            <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M12 21c-4.418 0-8-5.373-8-10A8 8 0 0 1 20 11c0 4.627-3.582 10-8 10z"></path><circle cx="12" cy="11" r="3"></circle></svg>
            Check-In
        </a>
        <a class="action-btn" href="tracking.php">
            <!-- Map/Route SVG -->
            <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><polyline points="1 6 1 22 17 22"></polyline><polygon points="23 2 23 16 7 16 7 2 23 2"></polygon><line x1="7" y1="16" x2="17" y2="22"></line></svg>
            Tracking
        </a>
    </div>
    <a class="logout" href="logout.php">Logout</a>
</div>

<!-- Deep Link & QR Code Section -->
<div class="container" style="margin-top:40px;">
    <h3>Open in Mobile App</h3>
    <div id="deeplink-desktop" style="display:none;">
        <p>Scan this QR code with your phone to open the app:</p>
        <div id="deeplink-qrcode"></div>
    </div>
    <div id="deeplink-mobile" style="display:none;">
        <button class="button" onclick="openAppWithSession()">Open in App</button>
        <div id="deeplink-fallback" style="display:none; margin-top:20px;">
            <p>It looks like you don't have the app installed.</p>
            <a class="button" href="https://play.google.com/store/apps/details?id=com.yourcompany.yourapp" target="_blank">Download the app</a>
        </div>
    </div>
</div>
<!-- End Deep Link & QR Code Section -->

<div id="location-debug">Location log debug info will appear here.</div>
<script>
function sendLocation() {
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(function(pos) {
            var lat = pos.coords.latitude;
            var lng = pos.coords.longitude;
            var debugDiv = document.getElementById('location-debug');
            debugDiv.innerHTML = '<b>Coordinates:</b> ' + lat + ', ' + lng + ' <br><b>Time:</b> ' + new Date().toLocaleTimeString() + '<br>Sending to server...';
            var xhr = new XMLHttpRequest();
            xhr.open('POST', 'log_location.php', true);
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            xhr.onload = function() {
                debugDiv.innerHTML += '<br><b>Server response:</b> ' + xhr.responseText;
            };
            xhr.send('lat=' + encodeURIComponent(lat) + '&lng=' + encodeURIComponent(lng));
        }, function(error) {
            var debugDiv = document.getElementById('location-debug');
            debugDiv.innerHTML = '<b>Geolocation error:</b> ' + error.message;
        });
    }
}
sendLocation();
setInterval(sendLocation, 15000);

// Deep link and QR code logic
function isMobile() {
  return /Android|iPhone|iPad|iPod|Opera Mini|IEMobile|WPDesktop/i.test(navigator.userAgent);
}

function openAppWithSession() {
  var now = new Date().getTime();
  setTimeout(function () {
    if (new Date().getTime() - now < 2000) {
      document.getElementById('deeplink-fallback').style.display = 'block';
    }
  }, 1500);
  // Try to open the app with session id
  window.location = "<?php echo $deep_link; ?>";
}

window.onload = function() {
  if (!isMobile()) {
    document.getElementById('deeplink-desktop').style.display = 'block';
    new QRCode(document.getElementById("deeplink-qrcode"), {
      text: "<?php echo $deep_link; ?>",
      width: 200,
      height: 200
    });
  } else {
    document.getElementById('deeplink-mobile').style.display = 'block';
  }
}
</script>
</body>
</html>