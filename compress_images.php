<?php
session_start();
require_once 'includes/db_connect.php';
require_once 'includes/functions.php';

header('Content-Type: application/json');

if (!isset($_SESSION['user_id']) || !isAdmin($_SESSION['user_id'])) {
    echo json_encode(['success' => false, 'error' => 'Unauthorized']);
    exit;
}

$user_id = $_SESSION['user_id'];
$folder_id = isset($_GET['folder_id']) ? (int)$_GET['folder_id'] : null;
if (!$folder_id) {
    echo json_encode(['success' => false, 'error' => 'No folder specified']);
    exit;
}

function getAllImageFiles($folder_id, $pdo) {
    $files = [];
    $stmt = $pdo->prepare("SELECT path, name FROM files WHERE folder_id = ?");
    $stmt->execute([$folder_id]);
    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $ext = strtolower(pathinfo($row['name'], PATHINFO_EXTENSION));
        if (in_array($ext, ['jpg', 'jpeg', 'png'])) {
            $files[] = __DIR__ . '/' . $row['path'];
        }
    }
    $stmt = $pdo->prepare("SELECT id FROM folders WHERE parent_id = ?");
    $stmt->execute([$folder_id]);
    while ($sub = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $files = array_merge($files, getAllImageFiles($sub['id'], $pdo));
    }
    return $files;
}

function compressImage($file, $pdo) {
    $logfile = __DIR__ . '/compress_images.log';
    $ext = strtolower(pathinfo($file, PATHINFO_EXTENSION));
    if (!in_array($ext, ['jpg', 'jpeg', 'png'])) return false;
    
    // Load image
    if ($ext === 'jpg' || $ext === 'jpeg') {
        $img = @imagecreatefromjpeg($file);
    } elseif ($ext === 'png') {
        $img = @imagecreatefrompng($file);
    } else {
        return false;
    }
    if (!$img) return false;

    $orig_width = imagesx($img);
    $orig_height = imagesy($img);
    $max_width = 1080;
    $scale = min(1, $max_width / $orig_width);
    $new_width = ($scale < 1) ? intval($orig_width * $scale) : $orig_width;
    $new_height = ($scale < 1) ? intval($orig_height * $scale) : $orig_height;

    // Resize if needed
    if ($scale < 1) {
        $resized = imagecreatetruecolor($new_width, $new_height);
        imagecopyresampled($resized, $img, 0, 0, 0, 0, $new_width, $new_height, $orig_width, $orig_height);
        imagedestroy($img);
        $img = $resized;
    }

    $orig_size = filesize($file);

    // Always save as JPEG, enforce max size 250KB
    $jpeg_quality = 50;
    $min_quality = 20;
    $step = 5;
    $max_size = 250 * 1024;
    $new_file = $file;
    $renamed = false;
    if ($ext === 'png') {
        $new_file = preg_replace('/\.[^.]+$/', '.jpg', $file);
        $renamed = true;
        error_log("Renaming PNG $file to $new_file\n", 3, $logfile);
    }
    $tmpFile = $new_file . '.tmp';
    $final_quality = $jpeg_quality;
    do {
        imagejpeg($img, $tmpFile, $jpeg_quality);
        $size = filesize($tmpFile);
        if ($size <= $max_size) {
            $final_quality = $jpeg_quality;
            break;
        }
        $jpeg_quality -= $step;
    } while ($jpeg_quality >= $min_quality);
    // If still too large, keep the last attempt
    if (!file_exists($tmpFile)) {
        imagedestroy($img);
        error_log("Failed to create tmp file for $file\n", 3, $logfile);
        return false;
    }
    // Move tmp to final
    $result = rename($tmpFile, $new_file);
    $new_size = filesize($new_file);
    imagedestroy($img);
    if ($result) {
        if ($renamed) {
            if (unlink($file)) {
                error_log("Deleted original PNG $file after conversion\n", 3, $logfile);
            } else {
                error_log("Failed to delete original PNG $file after conversion\n", 3, $logfile);
            }
            // Update DB to reflect new file path and name
            $rel_old = str_replace(__DIR__ . '/', '', $file);
            $rel_new = str_replace(__DIR__ . '/', '', $new_file);
            $stmt = $pdo->prepare("UPDATE files SET path = ?, name = ?, type = 'image/jpeg' WHERE path = ?");
            $new_name = preg_replace('/\.[^.]+$/', '.jpg', basename($file));
            if ($stmt->execute([$rel_new, $new_name, $rel_old])) {
                error_log("DB updated: $rel_old -> $rel_new, name: $new_name\n", 3, $logfile);
            } else {
                error_log("DB update FAILED for $rel_old -> $rel_new\n", 3, $logfile);
            }
        }
        error_log("Compressed $file: orig_size={$orig_size}, new_size={$new_size}, final_quality={$final_quality}\n", 3, $logfile);
        return true;
    } else {
        if ($renamed && file_exists($new_file)) unlink($new_file);
        if (file_exists($tmpFile)) unlink($tmpFile);
        error_log("Failed to rename tmp file to $new_file for $file\n", 3, $logfile);
        return false;
    }
}

try {
    $images = getAllImageFiles($folder_id, $pdo);
    $compressed = 0;
    foreach ($images as $img) {
        if (file_exists($img)) {
            if (compressImage($img, $pdo)) {
                $compressed++;
            }
        }
    }
    echo json_encode([
        'success' => true,
        'compressed' => $compressed,
        'total' => count($images)
    ]);
} catch (Exception $e) {
    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
} 