<?php
session_start();
require_once 'includes/db_connect.php';
require_once 'includes/functions.php';

if (!isset($_SESSION['user_id'])) {
    die("Unauthorized");
}

$user_id = $_SESSION['user_id'];
$folder_id = isset($_GET['folder_id']) ? (int)$_GET['folder_id'] : null;
$action = isset($_GET['action']) ? $_GET['action'] : null;

if (!$folder_id) {
    die("Invalid folder ID");
}

if (!isAdmin($user_id)) {
    die("Only admins can perform this action.");
}

// Fetch folder name
$stmt = $pdo->prepare("SELECT name FROM folders WHERE id = ?");
$stmt->execute([$folder_id]);
$folder = $stmt->fetch(PDO::FETCH_ASSOC);
if (!$folder) {
    die("Folder not found.");
}
$folder_name = preg_replace('/[^A-Za-z0-9_\-]/', '_', $folder['name']);

// Set custom temp directory for compressed zips
$custom_tmp_dir = __DIR__ . '/compressed';
if (!is_dir($custom_tmp_dir)) {
    mkdir($custom_tmp_dir, 0777, true);
}
$zip_path = "$custom_tmp_dir/zip_{$user_id}_{$folder_id}.zip";
$progress_path = "$custom_tmp_dir/zip_progress_{$user_id}_{$folder_id}.json";

function getAllFilesAndSize($folder_id, $pdo) {
    $files = [];
    $total_size = 0;
    // Get files in this folder
    $stmt = $pdo->prepare("SELECT name, path, size FROM files WHERE folder_id = ?");
    $stmt->execute([$folder_id]);
    while ($file = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $file_path = __DIR__ . '/' . $file['path'];
        if (file_exists($file_path)) {
            $files[] = [
                'name' => $file['name'],
                'path' => $file_path,
                'size' => $file['size'],
                'rel' => $file['name']
            ];
            $total_size += $file['size'];
        }
    }
    // Recurse into subfolders
    $stmt = $pdo->prepare("SELECT id, name FROM folders WHERE parent_id = ?");
    $stmt->execute([$folder_id]);
    while ($subfolder = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $subfolder_files = getAllFilesAndSize($subfolder['id'], $pdo);
        foreach ($subfolder_files['files'] as $sf) {
            $sf['rel'] = $subfolder['name'] . '/' . (isset($sf['rel']) ? $sf['rel'] : $sf['name']);
            $files[] = $sf;
        }
        $total_size += $subfolder_files['total_size'];
    }
    return ['files' => $files, 'total_size' => $total_size];
}

if ($action === 'compress') {
    // Remove old progress/zip
    if (file_exists($progress_path)) unlink($progress_path);
    if (file_exists($zip_path)) unlink($zip_path);

    $all = getAllFilesAndSize($folder_id, $pdo);
    $files = $all['files'];
    $total_files = count($files);
    $total_size = $all['total_size'];
    // 10GB limit
    $max_size = 10 * 1024 * 1024 * 1024; // 10GB in bytes
    if ($total_size > $max_size) {
        $progress = [
            'total_files' => $total_files,
            'total_size' => $total_size,
            'done_files' => 0,
            'done_size' => 0,
            'percent' => 0,
            'status' => 'error',
            'error' => 'Folder too large to compress (limit is 10GB).'
        ];
        file_put_contents($progress_path, json_encode($progress));
        exit;
    }
    $progress = [
        'total_files' => $total_files,
        'total_size' => $total_size,
        'done_files' => 0,
        'done_size' => 0,
        'percent' => 0,
        'status' => 'compressing',
        'error' => null
    ];
    file_put_contents($progress_path, json_encode($progress));

    $zip = new ZipArchive();
    if ($zip->open($zip_path, ZipArchive::CREATE) !== TRUE) {
        $progress['status'] = 'error';
        $progress['error'] = 'Failed to create ZIP.';
        file_put_contents($progress_path, json_encode($progress));
        exit;
    }
    foreach ($files as $i => $file) {
        $zip->addFile($file['path'], $file['rel']);
        $progress['done_files'] = $i + 1;
        $progress['done_size'] += $file['size'];
        $progress['percent'] = $total_size > 0 ? round(100 * $progress['done_size'] / $total_size) : 100;
        file_put_contents($progress_path, json_encode($progress));
    }
    $zip->close();
    if (file_exists($zip_path)) {
        $progress['status'] = 'done';
        $progress['percent'] = 100;
    } else {
        $progress['status'] = 'error';
        $progress['error'] = 'ZIP file not created.';
    }
    file_put_contents($progress_path, json_encode($progress));
    exit;
}

if ($action === 'progress') {
    if (file_exists($progress_path)) {
        header('Content-Type: application/json');
        echo file_get_contents($progress_path);
    } else {
        echo json_encode(['status' => 'not_started']);
    }
    exit;
}

if ($action === 'download') {
    if (!file_exists($zip_path)) {
        die("ZIP not ready");
    }
    header('Content-Type: application/zip');
    header('Content-Disposition: attachment; filename="' . $folder_name . '.zip"');
    header('Content-Length: ' . filesize($zip_path));
    readfile($zip_path);
    // Delete ZIP and progress file after download
    if (file_exists($zip_path)) unlink($zip_path);
    if (file_exists($progress_path)) unlink($progress_path);
    exit;
}

// Legacy: direct download (for compatibility)
$zip = new ZipArchive();
$tmp_zip = tempnam(sys_get_temp_dir(), 'zip');
$zip->open($tmp_zip, ZipArchive::CREATE);

function addFolderToZip($folder_id, $zip, $pdo, $base_path = '') {
    $stmt = $pdo->prepare("SELECT name FROM folders WHERE id = ?");
    $stmt->execute([$folder_id]);
    $folder = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$folder) return;
    $current_path = $base_path . $folder['name'] . '/';
    $stmt = $pdo->prepare("SELECT name, path FROM files WHERE folder_id = ?");
    $stmt->execute([$folder_id]);
    while ($file = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $file_path = __DIR__ . '/' . $file['path'];
        if (file_exists($file_path)) {
            $zip->addFile($file_path, $current_path . $file['name']);
        }
    }
    $stmt = $pdo->prepare("SELECT id FROM folders WHERE parent_id = ?");
    $stmt->execute([$folder_id]);
    while ($subfolder = $stmt->fetch(PDO::FETCH_ASSOC)) {
        addFolderToZip($subfolder['id'], $zip, $pdo, $current_path);
    }
}

addFolderToZip($folder_id, $zip, $pdo);
$zip->close();
header('Content-Type: application/zip');
header('Content-Disposition: attachment; filename="' . $folder_name . '.zip"');
header('Content-Length: ' . filesize($tmp_zip));
readfile($tmp_zip);
unlink($tmp_zip);
exit; 