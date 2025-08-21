<?php
header('Content-Type: application/json');
require_once 'common.php';
require_once 'vendor/autoload.php';

// Include required files
include "../core/functions.php";
$db = new DBO();

use Firebase\JWT\JWT;
use Firebase\JWT\Key;

$token = isset($_REQUEST['token']) ? $_REQUEST['token'] : null;

if (!$token) {
    echo json_encode(['success' => false, 'error' => 'No token provided']);
    exit;
}

try {
    $decoded = JWT::decode($token, new Key($jwt_secret, 'HS256'));
    $user_id = $decoded->sub;
    $session_id = $decoded->session_id;
    $exp = $decoded->exp;

    // Check if token exists in DB and is not expired
    $stmt = $pdo->prepare('SELECT * FROM jwt_tokens WHERE user_id = ? AND session_id = ? AND token = ? AND expires_at > NOW()');
    $stmt->execute([$user_id, $session_id, $token]);
    $row = $stmt->fetch();
    if ($row) {
        // Fetch setup status from users table
        $stmt2 = $pdo->prepare('SELECT setup,name, loc_res, branch FROM org1_staff WHERE id = ?');
        $stmt2->execute([$user_id]);
        $user = $stmt2->fetch();
        $user_name = $user ? $user['name'] : '';
        $setup = $user ? intval($user['setup']) : 0;
        $u_res = $user ? intval($user['loc_res']) : 0;
        $b_id = $user ? $user['branch'] : 4;
        
        

        // Get branch info (no cid filter)
        $branchList = $db->query(1, "SELECT latitude, longitude, off_radius, location_restriction, from_time, to_time FROM branches WHERE id = ?", [$b_id]);
        
        if (!$branchList || !isset($branchList[0])) {
            echo json_encode(['error' => 'Branch not found']);
            http_response_code(404);
            exit;
        }
        
        $branch = $branchList[0];
        
        $b_lat = (float)$branch['latitude'];
        $b_lng = (float)$branch['longitude'];
        $off_rad = (float)$branch['off_radius'];
        $from_time = $branch['from_time'];
        $to_time = $branch['to_time'];
        $b_res = (int)$branch['location_restriction'];


        
        echo json_encode([
            'success' => true,
            'user_id' => $user_id, 
            'setup' => $setup, 
            'user_name' => $user_name, 
            'u_res' => $u_res,
            'b_res' => $b_res,
            'b_id' => $b_id,
            'b_lat' => $b_lat,
            'b_lng' => $b_lng,
            'off_rad' => $off_rad,
            'from_time' => $from_time,
            'to_time' => $to_time
            
        ]);

    } else {
        echo json_encode(['success' => false, 'error' => 'Invalid token']);
    }
} catch (Exception $e) {
    echo json_encode(['success' => false, 'error' => 'Invalid token']);
} 