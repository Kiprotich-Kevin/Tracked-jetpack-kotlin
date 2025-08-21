<?php
session_start();
require_once 'includes/db_connect.php';
require_once 'includes/functions.php';

// Enable error reporting for debugging
ini_set('display_errors', 0); // Set to 0 in production
ini_set('log_errors', 1);
error_reporting(E_ALL);

if (!isset($_SESSION['user_id'])) {
    header("Location: login.php");
    exit();
}

$user_id = $_SESSION['user_id'];
$parent_id = isset($_GET['folder_id']) ? (int)$_GET['folder_id'] : null;
$search_term = isset($_GET['search']) ? trim(sanitize($_GET['search'])) : '';
$error = '';
$success = '';

// Handle session-based messages
if (isset($_SESSION['success'])) {
    $success = $_SESSION['success'];
    unset($_SESSION['success']);
}
if (isset($_SESSION['error'])) {
    $error = $_SESSION['error'];
    unset($_SESSION['error']);
}

// Generate CSRF token if not set
if (!isset($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}
$csrf_token = $_SESSION['csrf_token'];

$upload_dir = __DIR__ . '/Uploads';
if (!is_dir($upload_dir)) {
    if (!mkdir($upload_dir, 0755, true)) {
        $_SESSION['error'] = "Failed to create uploads directory.";
        error_log("Failed to create uploads directory: $upload_dir");
        header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
        exit();
    }
}
if (!is_writable($upload_dir)) {
    $_SESSION['error'] = "Uploads directory is not writable.";
    error_log("Uploads directory not writable: $upload_dir");
    header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
    exit();
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // Verify CSRF token
    if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== $_SESSION['csrf_token']) {
        $_SESSION['error'] = "Invalid CSRF token.";
        error_log("CSRF token validation failed for user $user_id");
        header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
        exit();
    }

    // Create Folder
    if (isset($_POST['create_folder'])) {
        $folder_name = sanitize($_POST['folder_name']);
        if (!empty($folder_name)) {
            if ($parent_id === null || checkPermission($user_id, 'folder', $parent_id, 'upload')) {
                try {
                    $stmt = $pdo->prepare("INSERT INTO folders (name, parent_id, owner_id) VALUES (?, ?, ?)");
                    $stmt->execute([$folder_name, $parent_id, $user_id]);
                    $_SESSION['success'] = "Folder '$folder_name' created.";
                } catch (Exception $e) {
                    $_SESSION['error'] = "Failed to create folder: " . $e->getMessage();
                    error_log("Create folder error: " . $e->getMessage());
                }
            } else {
                $_SESSION['error'] = "You do not have permission to create folders here.";
            }
        } else {
            $_SESSION['error'] = "Folder name is required.";
        }
        header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
        exit();
    }

    // Upload Files
    if (isset($_FILES['files'])) {
        $files = $_FILES['files'];
        $success_count = 0;
        $error_messages = [];
        $allowed_types = ['image/jpeg', 'image/png', 'application/pdf'];
        $max_size = 15 * 1024 * 1024; // 15MB
        $max_size_text = '15MB';

        // Reorganize $_FILES array for easier processing
        $file_array = [];
        foreach ($files['name'] as $key => $name) {
            $file_array[] = [
                'name' => $files['name'][$key],
                'type' => $files['type'][$key],
                'tmp_name' => $files['tmp_name'][$key],
                'error' => $files['error'][$key],
                'size' => $files['size'][$key]
            ];
        }

        if ($parent_id === null || checkPermission($user_id, 'folder', $parent_id, 'upload')) {
            foreach ($file_array as $file) {
                if ($file['error'] === UPLOAD_ERR_OK) {
                    if (in_array($file['type'], $allowed_types) && $file['size'] <= $max_size) {
                        $ext = pathinfo($file['name'], PATHINFO_EXTENSION);
                        $filename = $user_id . '_' . time() . '_' . uniqid() . '.' . $ext;
                        $path = $upload_dir . '/' . $filename;
                        if (is_writable($upload_dir) && is_uploaded_file($file['tmp_name'])) {
                            if (move_uploaded_file($file['tmp_name'], $path)) {
                                try {
                                    $stmt = $pdo->prepare("INSERT INTO files (folder_id, name, path, type, size, owner_id) VALUES (?, ?, ?, ?, ?, ?)");
                                    $stmt->execute([$parent_id, $file['name'], "Uploads/$filename", $file['type'], $file['size'], $user_id]);
                                    $success_count++;
                                } catch (Exception $e) {
                                    $error_messages[] = "Failed to store metadata for '{$file['name']}': " . $e->getMessage();
                                    unlink($path);
                                    error_log("File upload error for '{$file['name']}': " . $e->getMessage());
                                }
                            } else {
                                $error_messages[] = "Failed to move uploaded file '{$file['name']}'.";
                                error_log("Upload error: Failed to move " . $file['tmp_name'] . " to $path for '{$file['name']}'.");
                            }
                        } else {
                            $error_messages[] = "Upload directory is not writable for '{$file['name']}'.";
                            error_log("Upload error for '{$file['name']}': Directory $upload_dir writable: " . (is_writable($upload_dir) ? 'yes' : 'no'));
                        }
                    } else {
                        $error_messages[] = "Invalid file type or size exceeds $max_size_text for '{$file['name']}'.";
                        error_log("Upload error: name={$file['name']}, size={$file['size']}, type={$file['type']}");
                    }
                } else {
                    error_log("Upload error: name={$file['name']}, size={$file['size']}, type={$file['type']}, error={$file['error']}");
                    switch ($file['error']) {
                        case UPLOAD_ERR_INI_SIZE:
                            $error_messages[] = "File '{$file['name']}' exceeds maximum upload size (15MB).";
                            break;
                        case UPLOAD_ERR_FORM_SIZE:
                            $error_messages[] = "File '{$file['name']}' exceeds form size limit.";
                            break;
                        case UPLOAD_ERR_PARTIAL:
                            $error_messages[] = "File '{$file['name']}' was only partially uploaded.";
                            break;
                        case UPLOAD_ERR_NO_FILE:
                            // Skip if no file was uploaded
                            continue;
                        case UPLOAD_ERR_NO_TMP_DIR:
                            $error_messages[] = "Missing temporary folder for '{$file['name']}'.";
                            break;
                        case UPLOAD_ERR_CANT_WRITE:
                            $error_messages[] = "Failed to write file '{$file['name']}' to disk.";
                            break;
                        case UPLOAD_ERR_EXTENSION:
                            $error_messages[] = "A PHP extension stopped the upload of '{$file['name']}'.";
                            break;
                        default:
                            $error_messages[] = "Unknown upload error for '{$file['name']}': " . $file['error'];
                    }
                }
            }
        } else {
            $_SESSION['error'] = "You do not have permission to upload files here.";
            error_log("Permission denied for file upload by user $user_id in folder " . ($parent_id ?? 'root'));
            header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
            exit();
        }

        // Compile feedback
        if ($success_count > 0) {
            $_SESSION['success'] = "$success_count file(s) uploaded successfully.";
        }
        if (!empty($error_messages)) {
            $_SESSION['error'] = implode("<br>", $error_messages);
        }
        header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
        exit();
    }

    // Delete Folder
    if (isset($_POST['delete_folder'])) {
        $folder_id = (int)$_POST['folder_id'];
        if (isAdmin($user_id)) {
            try {
                $stmt = $pdo->prepare("DELETE FROM folders WHERE id = ?");
                $stmt->execute([$folder_id]);
                $_SESSION['success'] = "Folder deleted successfully.";
            } catch (Exception $e) {
                $_SESSION['error'] = "Failed to delete folder: " . $e->getMessage();
                error_log("Delete folder error: " . $e->getMessage());
            }
        } else {
            $_SESSION['error'] = "Only admins can delete folders.";
        }
        header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
        exit();
    }

    // Delete File
    if (isset($_POST['delete_file'])) {
        $file_id = (int)$_POST['file_id'];
        if (isAdmin($user_id)) {
            try {
                $stmt = $pdo->prepare("SELECT path FROM files WHERE id = ?");
                $stmt->execute([$file_id]);
                $file = $stmt->fetch(PDO::FETCH_ASSOC);
                if ($file && file_exists($file['path'])) {
                    unlink($file['path']);
                }
                $stmt = $pdo->prepare("DELETE FROM files WHERE id = ?");
                $stmt->execute([$file_id]);
                $_SESSION['success'] = "File deleted successfully.";
            } catch (Exception $e) {
                $_SESSION['error'] = "Failed to delete file: " . $e->getMessage();
                error_log("Delete file error: " . $e->getMessage());
            }
        } else {
            $_SESSION['error'] = "Only admins can delete files.";
        }
        header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
        exit();
    }

    // Set Permission
    if (isset($_POST['set_permission']) && isAdmin($user_id)) {
        $resource_type = $_POST['resource_type'];
        $resource_id = (int)$_POST['resource_id'];
        $target_user_id = (int)$_POST['user_id'];
        $role = isset($_POST['role']) ? $_POST['role'] : '';

        if ($resource_type !== 'folder') {
            $_SESSION['error'] = "Permissions can only be set on folders.";
        } elseif ($target_user_id <= 0) {
            $_SESSION['error'] = "Please select a valid user to share with.";
            error_log("Permission error: Invalid user_id $target_user_id for $resource_type $resource_id");
        } else {
            try {
                $stmt = $pdo->prepare("SELECT id, parent_id FROM folders WHERE id = ?");
                $stmt->execute([$resource_id]);
                $folder = $stmt->fetch(PDO::FETCH_ASSOC);
                if (!$folder) {
                    $_SESSION['error'] = "Invalid folder ID: $resource_id does not exist.";
                } elseif ($folder['parent_id'] !== null) {
                    $_SESSION['error'] = "Permissions can only be set on top-level folders.";
                } else {
                    $stmt = $pdo->prepare("SELECT id FROM users WHERE id = ?");
                    $stmt->execute([$target_user_id]);
                    if (!$stmt->fetch()) {
                        $_SESSION['error'] = "Invalid user ID: $target_user_id does not exist.";
                        error_log("Permission error: Invalid user_id $target_user_id for $resource_type $resource_id");
                    } elseif (in_array($role, ['Viewer', 'Editor'])) {
                        try {
                            $stmt = $pdo->prepare("INSERT INTO permissions (resource_type, resource_id, user_id, role) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE role = ?");
                            $stmt->execute(['folder', $resource_id, $target_user_id, $role, $role]);
                            $_SESSION['success'] = "Permission set for user $target_user_id on folder.";
                        } catch (Exception $e) {
                            $_SESSION['error'] = "Failed to set permission: " . $e->getMessage();
                            error_log("Permission error: " . $e->getMessage());
                        }
                    } else {
                        $_SESSION['error'] = "Invalid role specified.";
                    }
                }
            } catch (Exception $e) {
                $_SESSION['error'] = "Failed to validate folder/user: " . $e->getMessage();
                error_log("Permission validation error: " . $e->getMessage());
            }
        }
        header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
        exit();
    }

    // Remove Permission
    if (isset($_POST['remove_permission']) && isAdmin($user_id)) {
        $resource_type = $_POST['resource_type'];
        $resource_id = (int)$_POST['resource_id'];
        $target_user_id = (int)$_POST['user_id'];

        if ($resource_type !== 'folder') {
            $_SESSION['error'] = "Permissions can only be removed from folders.";
        } elseif ($target_user_id <= 0) {
            $_SESSION['error'] = "Please select a valid user to remove permissions for.";
            error_log("Remove permission error: Invalid user_id $target_user_id for $resource_type $resource_id");
        } else {
            try {
                $stmt = $pdo->prepare("SELECT id, parent_id FROM folders WHERE id = ?");
                $stmt->execute([$resource_id]);
                $folder = $stmt->fetch(PDO::FETCH_ASSOC);
                if (!$folder) {
                    $_SESSION['error'] = "Invalid folder ID: $resource_id does not exist.";
                } elseif ($folder['parent_id'] !== null) {
                    $_SESSION['error'] = "Permissions can only be removed from top-level folders.";
                } else {
                    $stmt = $pdo->prepare("SELECT id FROM users WHERE id = ?");
                    $stmt->execute([$target_user_id]);
                    if (!$stmt->fetch()) {
                        $_SESSION['error'] = "Invalid user ID: $target_user_id does not exist.";
                        error_log("Remove permission error: Invalid user_id $target_user_id for $resource_type $resource_id");
                    } else {
                        try {
                            $stmt = $pdo->prepare("DELETE FROM permissions WHERE resource_type = ? AND resource_id = ? AND user_id = ?");
                            $stmt->execute(['folder', $resource_id, $target_user_id]);
                            if ($stmt->rowCount() > 0) {
                                $_SESSION['success'] = "Permissions removed for user $target_user_id on folder.";
                            } else {
                                $_SESSION['error'] = "No permissions found for user $target_user_id on this folder.";
                            }
                        } catch (Exception $e) {
                            $_SESSION['error'] = "Failed to remove permission: " . $e->getMessage();
                            error_log("Remove permission error: " . $e->getMessage());
                        }
                    }
                }
            } catch (Exception $e) {
                $_SESSION['error'] = "Failed to validate folder/user: " . $e->getMessage();
                error_log("Remove permission validation error: " . $e->getMessage());
            }
        }
        header("Location: index.php" . ($parent_id ? "?folder_id=$parent_id" : ""));
        exit();
    }
}

// Fetch folders and files
$folders = [];
$files = [];
try {
    if (isAdmin($user_id)) {
        if ($search_term) {
            $stmt = $pdo->prepare("SELECT f.*, NULL AS role FROM folders f WHERE LOWER(f.name) LIKE LOWER(?)");
            $stmt->execute(["%$search_term%"]);
            $folders = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("Admin search folders, user $user_id, term='$search_term': " . json_encode($folders));

            $stmt = $pdo->prepare("SELECT f.*, NULL AS role FROM files f WHERE LOWER(f.name) LIKE LOWER(?)");
            $stmt->execute(["%$search_term%"]);
            $files = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("Admin search files, user $user_id, term='$search_term': " . json_encode($files));
        } else {
            $stmt = $pdo->prepare("SELECT f.*, NULL AS role FROM folders f WHERE f.parent_id <=> ?");
            $stmt->execute([$parent_id]);
            $folders = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("Admin folders for user $user_id, parent_id=" . ($parent_id ?? 'NULL') . ": " . json_encode($folders));

            $stmt = $pdo->prepare("SELECT f.*, NULL AS role FROM files f WHERE f.folder_id <=> ?");
            $stmt->execute([$parent_id]);
            $files = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("Admin files for user $user_id, folder_id=" . ($parent_id ?? 'NULL') . ": " . json_encode($files));
        }
    } else {
        $stmt = $pdo->prepare("SELECT resource_id, role FROM permissions WHERE resource_type = 'folder' AND user_id = ?");
        $stmt->execute([$user_id]);
        $permitted_parents = $stmt->fetchAll(PDO::FETCH_ASSOC);
        $permitted_parent_ids = array_column($permitted_parents, 'resource_id');
        error_log("User $user_id permitted parent IDs: " . json_encode($permitted_parent_ids));

        if ($search_term) {
            if (!empty($permitted_parent_ids)) {
                $placeholders = implode(',', array_fill(0, count($permitted_parent_ids), '?'));
                $query = "
                    WITH RECURSIVE folder_tree AS (
                        SELECT id, name, parent_id, owner_id
                        FROM folders
                        WHERE id IN ($placeholders)
                        UNION ALL
                        SELECT f.id, f.name, f.parent_id, f.owner_id
                        FROM folders f
                        INNER JOIN folder_tree ft ON f.parent_id = ft.id
                    )
                    SELECT f.*, NULL AS role
                    FROM folders f
                    WHERE LOWER(f.name) LIKE LOWER(?) AND (f.owner_id = ? OR f.id IN (SELECT id FROM folder_tree))
                ";
                $params = array_merge($permitted_parent_ids, ["%$search_term%", $user_id]);
                $stmt = $pdo->prepare($query);
                $stmt->execute($params) or error_log("Folder search error: " . print_r($pdo->errorInfo(), true));
                $folders = $stmt->fetchAll(PDO::FETCH_ASSOC);
                error_log("Search folders for user $user_id, term='$search_term': " . json_encode($folders));

                $query = "
                    WITH RECURSIVE folder_tree AS (
                        SELECT id, name, parent_id, owner_id
                        FROM folders
                        WHERE id IN ($placeholders)
                        UNION ALL
                        SELECT f.id, f.name, f.parent_id, f.owner_id
                        FROM folders f
                        INNER JOIN folder_tree ft ON f.parent_id = ft.id
                    )
                    SELECT f.*, NULL AS role
                    FROM files f
                    WHERE LOWER(f.name) LIKE LOWER(?) AND (f.owner_id = ? OR f.folder_id IN (SELECT id FROM folder_tree))
                ";
                $params = array_merge($permitted_parent_ids, ["%$search_term%", $user_id]);
                $stmt = $pdo->prepare($query);
                $stmt->execute($params) or error_log("File search error: " . print_r($pdo->errorInfo(), true));
                $files = $stmt->fetchAll(PDO::FETCH_ASSOC);
                error_log("Search files for user $user_id, term='$search_term': " . json_encode($files));
            } else {
                $stmt = $pdo->prepare("SELECT f.*, NULL AS role FROM folders f WHERE LOWER(f.name) LIKE LOWER(?) AND f.owner_id = ?");
                $stmt->execute(["%$search_term%", $user_id]);
                $folders = $stmt->fetchAll(PDO::FETCH_ASSOC);
                error_log("Search owned folders for user $user_id, term='$search_term': " . json_encode($folders));

                $stmt = $pdo->prepare("SELECT f.*, NULL AS role FROM files f WHERE LOWER(f.name) LIKE LOWER(?) AND f.owner_id = ?");
                $stmt->execute(["%$search_term%", $user_id]);
                $files = $stmt->fetchAll(PDO::FETCH_ASSOC);
                error_log("Search owned files for user $user_id, term='$search_term': " . json_encode($files));
            }
        } else {
            if ($parent_id === null) {
                if (!empty($permitted_parent_ids)) {
                    $placeholders = implode(',', array_fill(0, count($permitted_parent_ids), '?'));
                    $stmt = $pdo->prepare("
                        SELECT f.*, NULL AS role
                        FROM folders f
                        WHERE f.parent_id IS NULL AND (f.owner_id = ? OR f.id IN ($placeholders))
                    ");
                    $params = array_merge([$user_id], $permitted_parent_ids);
                    $stmt->execute($params);
                    $folders = $stmt->fetchAll(PDO::FETCH_ASSOC);
                    error_log("Root folders for user $user_id: " . json_encode($folders));
                } else {
                    $stmt = $pdo->prepare("SELECT f.*, NULL AS role FROM folders f WHERE f.parent_id IS NULL AND f.owner_id = ?");
                    $stmt->execute([$user_id]);
                    $folders = $stmt->fetchAll(PDO::FETCH_ASSOC);
                    error_log("Root owned folders for user $user_id: " . json_encode($folders));
                }

                $stmt = $pdo->prepare("SELECT f.*, NULL AS role FROM files f WHERE f.folder_id IS NULL AND f.owner_id = ?");
                $stmt->execute([$user_id]);
                $files = $stmt->fetchAll(PDO::FETCH_ASSOC);
                error_log("Root files for user $user_id: " . json_encode($files));
            } else {
                $top_level_parent_id = getTopLevelParent($parent_id);
                error_log("User $user_id, parent_id=$parent_id, top_level_parent=$top_level_parent_id");
                if ($top_level_parent_id && (in_array($top_level_parent_id, $permitted_parent_ids) || checkPermission($user_id, 'folder', $parent_id, 'view'))) {
                    $query = "
                        WITH RECURSIVE folder_tree AS (
                            SELECT id, name, parent_id, owner_id
                            FROM folders
                            WHERE id = ?
                            UNION ALL
                            SELECT f.id, f.name, f.parent_id, f.owner_id
                            FROM folders f
                            INNER JOIN folder_tree ft ON f.parent_id = ft.id
                        )
                        SELECT f.*, NULL AS role
                        FROM folders f
                        WHERE f.parent_id <=> ?
                    ";
                    $stmt = $pdo->prepare($query);
                    $stmt->execute([$top_level_parent_id, $parent_id]);
                    $folders = $stmt->fetchAll(PDO::FETCH_ASSOC);
                    error_log("Folders for user $user_id, parent_id=$parent_id: " . json_encode($folders));

                    $query = "
                        SELECT f.*, NULL AS role
                        FROM files f
                        WHERE f.folder_id = ?
                    ";
                    $stmt = $pdo->prepare($query);
                    $stmt->execute([$parent_id]);
                    $files = $stmt->fetchAll(PDO::FETCH_ASSOC);
                    error_log("Files for user $user_id, folder_id=$parent_id: " . json_encode($files));
                } else {
                    $_SESSION['error'] = "You do not have permission to access this folder.";
                    error_log("Access denied for user $user_id, parent_id=$parent_id");
                }
            }
        }
    }
} catch (Exception $e) {
    $_SESSION['error'] = "Failed to fetch folders/files: " . $e->getMessage();
    error_log("Query error: " . $e->getMessage());
}

// Fetch users and folder permissions
$users = [];
$folder_users = [];
if (isAdmin($user_id)) {
    try {
        $stmt = $pdo->prepare("SELECT id, username FROM users WHERE id != ?");
        $stmt->execute([$user_id]);
        $users = $stmt->fetchAll(PDO::FETCH_ASSOC);

        foreach ($folders as $folder) {
            if ($folder['parent_id'] === null) {
                $stmt = $pdo->prepare("SELECT u.id, u.username, p.role FROM permissions p JOIN users u ON p.user_id = u.id WHERE p.resource_type = 'folder' AND p.resource_id = ?");
                $stmt->execute([$folder['id']]);
                $folder_users[$folder['id']] = $stmt->fetchAll(PDO::FETCH_ASSOC);
            }
        }
    } catch (Exception $e) {
        $_SESSION['error'] = "Failed to fetch users or permissions: " . $e->getMessage();
        error_log("Users/permissions error: " . $e->getMessage());
    }
}

// Build breadcrumb
$breadcrumb = [];
if ($parent_id && !$search_term) {
    $current_id = $parent_id;
    while ($current_id) {
        $stmt = $pdo->prepare("SELECT id, name, parent_id FROM folders WHERE id = ?");
        $stmt->execute([$current_id]);
        $folder = $stmt->fetch(PDO::FETCH_ASSOC);
        if ($folder) {
            $breadcrumb[] = $folder;
            $current_id = $folder['parent_id'];
        } else {
            break;
        }
    }
    $breadcrumb = array_reverse($breadcrumb);
}

// Place this at the top, before the HTML
function getFolderSize($folder_id, $pdo) {
    $size = 0;
    // Files in this folder
    $stmt = $pdo->prepare("SELECT size FROM files WHERE folder_id = ?");
    $stmt->execute([$folder_id]);
    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $size += (int)$row['size'];
    }
    // Recurse into subfolders
    $stmt = $pdo->prepare("SELECT id FROM folders WHERE parent_id = ?");
    $stmt->execute([$folder_id]);
    while ($sub = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $size += getFolderSize($sub['id'], $pdo);
    }
    return $size;
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Brisk Photos</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css">
    <link rel="stylesheet" href="css/style.css">
    <style>
        body {
            margin: 0;
            min-height: 100vh;
            font-family: Arial, sans-serif;
        }
        main {
            margin-bottom: 40px;
            padding: 20px;
        }
        header {
            background: #007bff;
            color: white;
            padding: 10px 20px;
        }
        header h1 {
            margin: 0;
            font-size: 24px;
        }
        header p {
            margin: 10px 0 0;
        }
        header a {
            color: white;
            text-decoration: none;
            margin-left: 10px;
        }
        header a:hover {
            text-decoration: underline;
        }
        footer {
            padding: 5px;
            font-size: 12px;
            height: 30px;
            line-height: 30px;
            text-align: center;
            background: #f5f5f5;
            color: #666;
            position: static;
            width: 100%;
            box-sizing: border-box;
        }
        .action-bar {
            display: flex;
            align-items: center;
            gap: 15px;
            margin-bottom: 20px;
            flex-wrap: wrap;
        }
        .action-bar form {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .action-bar input[type="text"],
        .action-bar input[type="file"] {
            padding: 6px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 14px;
        }
        .action-bar input[type="text"] {
            width: 200px;
        }
        .action-bar input[type="file"] {
            width: auto;
        }
        .action-bar button {
            padding: 6px 12px;
            background: #007bff;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
        }
        .action-bar button:hover {
            background: #0056b3;
        }
        .action-bar .clear-button {
            background: #6c757d;
        }
        .action-bar .clear-button:hover {
            background: #5a6268;
        }
        .error {
            color: red;
            margin-bottom: 10px;
        }
        .success {
            color: green;
            margin-bottom: 10px;
        }
        .no-results {
            color: #666;
            font-style: italic;
        }
        nav {
            margin-bottom: 20px;
        }
        nav a {
            color: #007bff;
            text-decoration: none;
        }
        nav a:hover {
            text-decoration: underline;
        }
        .folder-list, .file-grid {
            margin-top: 20px;
        }
        .folder-item, .file-item {
            display: flex;
            align-items: center;
            padding: 10px;
            border-bottom: 1px solid #eee;
        }
        .folder-icon i {
            font-size: 24px;
            color: #007bff;
            margin-right: 10px;
        }
        .folder-details a {
            color: #007bff;
            text-decoration: none;
        }
        .folder-details a:hover {
            text-decoration: underline;
        }
        .folder-role {
            color: #666;
            font-size: 12px;
            margin-left: 10px;
        }
        .folder-actions {
            margin-left: auto;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .access-dropdown {
            position: relative;
            display: inline-block;
        }
        .access-dropdown button {
            background: #6c757d;
            color: white;
            padding: 5px 10px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }
        .access-dropdown button:hover {
            background: #5a6268;
        }
        .dropdown-content {
            display: none;
            position: absolute;
            background: white;
            min-width: 150px;
            box-shadow: 0 0 5px rgba(0,0,0,0.2);
            border-radius: 4px;
            z-index: 1;
            margin-top: 5px;
            right: 0;
        }
        .dropdown-content.show {
            display: block;
        }
        .dropdown-content p {
            margin: 0;
            padding: 8px;
            border-bottom: 1px solid #eee;
        }
        .dropdown-content p:last-child {
            border-bottom: none;
        }
        .no-access {
            color: #666;
            font-style: italic;
        }
        .permission-form, .remove-permission-form {
            display: flex;
            gap: 5px;
        }
        .permission-form select, .permission-form button,
        .remove-permission-form select, .remove-permission-form button {
            padding: 5px;
            font-size: 14px;
        }
        .button.delete {
            background: #dc3545;
            color: white;
        }
        .button.delete:hover {
            background: #c82333;
        }
        .button.remove {
            background: #ff851b;
            color: white;
        }
        .button.remove:hover {
            background: #e57300;
        }
        .file-item img.thumbnail {
            max-width: 100px;
            max-height: 100px;
            margin-right: 10px;
        }
        .file-info a {
            color: #007bff;
            text-decoration: none;
        }
        .file-info a:hover {
            text-decoration: underline;
        }
        .compression-progress {
            display: inline-block;
            margin-left: 10px;
            font-size: 12px;
            color: #555;
        }
        @media (max-width: 768px) {
            .action-bar {
                flex-direction: column;
                align-items: flex-start;
            }
            .action-bar form {
                width: 100%;
                flex-direction: column;
                align-items: flex-start;
            }
            .action-bar input[type="text"],
            .action-bar input[type="file"] {
                width: 100%;
            }
            .action-bar button {
                width: 100%;
            }
            .folder-actions {
                flex-direction: column;
                align-items: flex-end;
            }
            .permission-form, .remove-permission-form {
                width: 100%;
                flex-direction: column;
            }
            .permission-form select, .remove-permission-form select {
                width: 100%;
            }
            .permission-form button, .remove-permission-form button {
                width: 100%;
            }
        }
        .button.compress-btn { background: #007bff; color: white; border: none; border-radius: 4px; padding: 6px 12px; cursor: pointer; font-size: 14px; display: inline-flex; align-items: center; }
        .button.compressing { background: #ff9800 !important; color: white; }
        .button.download-ready { background: #28a745 !important; color: white; }
        .button .spinner { border: 2px solid #f3f3f3; border-top: 2px solid #fff; border-right: 2px solid #fff; border-radius: 50%; width: 16px; height: 16px; animation: spin 1s linear infinite; display: inline-block; margin-right: 6px; }
        @keyframes spin { 100% { transform: rotate(360deg); } }
    </style>
</head>
<body>
    <header>
        <h1>Brisk Photos</h1>
        <p>
            Welcome, <?php echo htmlspecialchars($_SESSION['username']); ?> | 
            <a href="logout.php">Logout</a>
        </p>
    </header>
    <main>
        <?php if ($error): ?>
            <p class="error"><?php echo htmlspecialchars($error); ?></p>
        <?php endif; ?>
        <?php if ($success): ?>
            <p class="success"><?php echo htmlspecialchars($success); ?></p>
        <?php endif; ?>

        <div class="action-bar">
            <form class="search-form" method="GET" action="index.php">
                <input type="text" name="search" value="<?php echo htmlspecialchars($search_term); ?>" placeholder="Search folders or files..." />
                <button type="submit">Search</button>
                <button type="button" class="clear-button" onclick="clearSearch()">Clear</button>
            </form>

            <?php if (!$search_term && (isAdmin($user_id) || ($parent_id !== null && checkPermission($user_id, 'folder', $parent_id, 'upload')))): ?>
                <form method="POST" class="create-folder-form">
                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                    <input type="text" name="folder_name" placeholder="New folder name" required />
                    <button type="submit" name="create_folder">Create Folder</button>
                </form>
            <?php endif; ?>

            <?php if (!$search_term && $parent_id !== null && (isAdmin($user_id) || checkPermission($user_id, 'folder', $parent_id, 'upload'))): ?>
                <form method="POST" enctype="multipart/form-data" class="upload-file-form">
                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                    <input type="file" name="files[]" accept="image/jpeg,image/png,application/pdf" multiple />
                    <button type="submit">Upload Files</button>
                </form>
            <?php endif; ?>
        </div>

        <?php if (!$search_term): ?>
            <nav>
                <a href="index.php">Home</a>
                <?php foreach ($breadcrumb as $folder): ?>
                    / <a href="?folder_id=<?php echo $folder['id']; ?>"><?php echo htmlspecialchars($folder['name']); ?></a>
                <?php endforeach; ?>
            </nav>
        <?php endif; ?>

        <h3>Folders</h3>
        <?php if (empty($folders) && $search_term): ?>
            <p class="no-results">No folders found matching "<?php echo htmlspecialchars($search_term); ?>"</p>
        <?php elseif (empty($folders)): ?>
            <p class="no-results">No folders available.</p>
        <?php else: ?>
            <div class="folder-list">
                <?php foreach ($folders as $folder): ?>
                    <div class="folder-item">
                        <div class="folder-icon">
                            <i class="fas fa-folder"></i>
                        </div>
                        <div class="folder-details">
                            <a href="?folder_id=<?php echo $folder['id']; ?>" class="folder-name"><?php echo htmlspecialchars($folder['name']); ?></a>
                            <span class="folder-size" style="color:#888; font-size:12px; margin-left:8px;">
                                (<?php 
                                    $size = getFolderSize($folder['id'], $pdo); 
                                    echo number_format($size / (1024*1024), 2); 
                                ?> MB)
                            </span>
                            <span class="folder-role">(Role: <?php echo isAdmin($user_id) ? 'Admin' : 'Viewer'; ?>)</span>
                        </div>
                        <div class="folder-actions">
                            <?php if (isAdmin($user_id) && $folder['parent_id'] === null): ?>
                                <?php if (empty($users)): ?>
                                    <span class="no-users">No users available to share with.</span>
                                <?php else: ?>
                                    <form method="POST" class="permission-form">
                                        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                                        <input type="hidden" name="resource_type" value="folder">
                                        <input type="hidden" name="resource_id" value="<?php echo $folder['id']; ?>">
                                        <select name="user_id" required>
                                            <option value="" disabled selected>Select a user</option>
                                            <?php foreach ($users as $u): ?>
                                                <option value="<?php echo $u['id']; ?>"><?php echo htmlspecialchars($u['username']); ?></option>
                                            <?php endforeach; ?>
                                        </select>
                                        <select name="role">
                                            <option value="Viewer">Viewer</option>
                                            <option value="Editor">Editor</option>
                                        </select>
                                        <button type="submit" name="set_permission">Share</button>
                                    </form>
                                    <form method="POST" class="remove-permission-form">
                                        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                                        <input type="hidden" name="resource_type" value="folder">
                                        <input type="hidden" name="resource_id" value="<?php echo $folder['id']; ?>">
                                        <select name="user_id" required>
                                            <option value="" disabled selected>Select user to remove</option>
                                            <?php foreach ($folder_users[$folder['id']] as $u): ?>
                                                <option value="<?php echo $u['id']; ?>"><?php echo htmlspecialchars($u['username']); ?></option>
                                            <?php endforeach; ?>
                                        </select>
                                        <button type="submit" name="remove_permission" class="button remove" onclick="return confirm('Are you sure you want to remove permissions for this user?');">Remove</button>
                                    </form>
                                <?php endif; ?>
                                <div class="access-dropdown">
                                    <button onclick="toggleDropdown(<?php echo $folder['id']; ?>)">Show Users</button>
                                    <div id="dropdown-<?php echo $folder['id']; ?>" class="dropdown-content">
                                        <?php if (empty($folder_users[$folder['id']])): ?>
                                            <p class="no-access">No users have access.</p>
                                        <?php else: ?>
                                            <?php foreach ($folder_users[$folder['id']] as $user): ?>
                                                <p><?php echo htmlspecialchars($user['username']) . ' - ' . $user['role']; ?></p>
                                            <?php endforeach; ?>
                                        <?php endif; ?>
                                    </div>
                                </div>
                            <?php endif; ?>
                            <?php if (isAdmin($user_id)): ?>
                                <form method="POST" class="delete-form" style="display:inline;">
                                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                                    <input type="hidden" name="folder_id" value="<?php echo $folder['id']; ?>">
                                    <button type="submit" name="delete_folder" class="button delete" onclick="return confirm('Are you sure you want to delete this folder?');">Delete</button>
                                </form>
                            <?php endif; ?>
                            <?php if (isAdmin($user_id)): ?>
                                <button class="button compress-img-btn" style="margin-left:5px; background:#6c757d; color:white;" onclick="compressImagesInFolder(<?php echo $folder['id']; ?>, this)">
                                    <i class="fas fa-compress"></i> Compress Img
                                </button>
                                <div class="compress-img-progress" id="compress-img-progress-<?php echo $folder['id']; ?>" style="display:none; font-size:12px; margin-top:2px;"></div>
                            <?php endif; ?>
                            <?php if (isAdmin($user_id)): ?>
                                <button id="compress-btn-<?php echo $folder['id']; ?>" class="button compress-btn compress-state" style="margin-left:5px; background:#007bff; color:white;" onclick="handleCompressClick(<?php echo $folder['id']; ?>, this)">
                                    <i class="fas fa-file-archive"></i> <span class="btn-text">Compress</span>
                                </button>
                                <div class="compression-progress-info" id="compression-progress-info-<?php echo $folder['id']; ?>" style="display:none; font-size:12px; margin-top:2px;"></div>
                            <?php endif; ?>
                        </div>
                    </div>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>

        <h3>Files</h3>
        <?php if (empty($files) && $search_term): ?>
            <p class="no-results">No files found matching "<?php echo htmlspecialchars($search_term); ?>"</p>
        <?php elseif (empty($files)): ?>
            <p class="no-results">No files available.</p>
        <?php else: ?>
            <div class="file-grid">
                <?php foreach ($files as $file): ?>
                    <?php
                    if ($file['type'] === 'application/pdf' && !file_exists('images/pdf_icon.png')) {
                        error_log("PDF icon missing: images/pdf_icon.png");
                    }
                    ?>
                    <div class="file-item">
                        <?php if (in_array($file['type'], ['image/jpeg', 'image/png'])): ?>
                            <a href="view_file.php?file_id=<?php echo $file['id']; ?>" target="_blank">
                                <img src="<?php echo htmlspecialchars($file['path']); ?>" alt="<?php echo htmlspecialchars($file['name']); ?>" class="thumbnail">
                            </a>
                            <div class="file-info">
                                <span style="font-size:12px; color:#888;">Size: <?php echo round($file['size'] / 1024, 2); ?> KB</span>
                            </div>
                            <?php if (isAdmin($user_id)): ?>
                                <form method="POST" style="display:inline;">
                                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                                    <input type="hidden" name="file_id" value="<?php echo $file['id']; ?>">
                                    <button type="submit" name="delete_file" class="button delete" onclick="return confirm('Are you sure you want to delete this file?');">Delete</button>
                                </form>
                            <?php endif; ?>
                        <?php elseif ($file['type'] === 'application/pdf' || strtolower(pathinfo($file['name'], PATHINFO_EXTENSION)) === 'pdf'): ?>
                            <a href="view_file.php?file_id=<?php echo $file['id']; ?>" target="_blank">
                                <img src="images/pdf_icon.png" alt="PDF Icon" class="thumbnail">
                            </a>
                            <div class="file-info">
                                <a href="view_file.php?file_id=<?php echo $file['id']; ?>" target="_blank"><?php echo htmlspecialchars($file['name']); ?></a>
                                (<?php echo $file['type']; ?>, <?php echo round($file['size'] / 1024, 2); ?> KB, Role: <?php echo isAdmin($user_id) ? 'Admin' : 'Viewer'; ?>)
                            </div>
                            <?php if (isAdmin($user_id)): ?>
                                <form method="POST" style="display:inline;">
                                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                                    <input type="hidden" name="file_id" value="<?php echo $file['id']; ?>">
                                    <button type="submit" name="delete_file" class="button delete" onclick="return confirm('Are you sure you want to delete this file?');">Delete</button>
                                </form>
                            <?php endif; ?>
                        <?php endif; ?>
                    </div>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
    </main>
    <footer>
        <p>© 2025 Brisk Photos</p>
    </footer>
    <script>
        function toggleDropdown(folderId) {
            const dropdown = document.getElementById('dropdown-' + folderId);
            dropdown.classList.toggle('show');
        }

        function clearSearch() {
            window.location.href = 'index.php<?php echo $parent_id ? "?folder_id=$parent_id" : ""; ?>';
        }

        // Image compression logic for upload form
        document.addEventListener('DOMContentLoaded', function () {
            const uploadForm = document.querySelector('.upload-file-form');
            if (!uploadForm) return;
            const fileInput = uploadForm.querySelector('input[type="file"][name="files[]"]');
            if (!fileInput) return;

            fileInput.addEventListener('change', async function (e) {
                const files = Array.from(fileInput.files);
                if (!files.length) return;

                // Process each file
                const processedFiles = await Promise.all(files.map(async (file) => {
                    if (!file.type.startsWith('image/')) return file; // Only compress images
                    return new Promise((resolve) => {
                        const img = new Image();
                        const reader = new FileReader();
                        reader.onload = function (event) {
                            img.src = event.target.result;
                        };
                        img.onload = function () {
                            const maxWidth = 1080;
                            const scaleFactor = Math.min(1, maxWidth / img.width);
                            const canvas = document.createElement('canvas');
                            canvas.width = Math.min(maxWidth, img.width);
                            canvas.height = img.height * scaleFactor;
                            const ctx = canvas.getContext('2d');
                            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                            canvas.toBlob(function (blob) {
                                // Use original file name, but force JPEG extension if compressing to JPEG
                                let ext = file.name.split('.').pop();
                                let name = file.name;
                                if (file.type !== 'image/jpeg') {
                                    name = file.name.replace(/\.[^.]+$/, '.jpg');
                                }
                                const compressedFile = new File([blob], name, { type: 'image/jpeg' });
                                resolve(compressedFile);
                            }, 'image/jpeg', 0.5);
                        };
                        reader.readAsDataURL(file);
                    });
                }));

                // Replace the FileList in the input with the processed files
                const dataTransfer = new DataTransfer();
                processedFiles.forEach(f => dataTransfer.items.add(f));
                fileInput.files = dataTransfer.files;
            });
        });

        function formatMB(bytes) {
            return (bytes / (1024*1024)).toFixed(2) + ' MB';
        }
        function handleCompressClick(folderId, btn) {
            if (btn.classList.contains('download-ready')) {
                window.location = 'download_folder.php?action=download&folder_id=' + folderId;
                return;
            }
            btn.disabled = true;
            btn.classList.remove('download-ready');
            btn.classList.add('compressing');
            btn.style.background = '#ff9800';
            btn.querySelector('.btn-text').textContent = 'Compressing...';
            btn.innerHTML = '<span class="spinner"></span> <span class="btn-text">Compressing...</span>';
            var infoDiv = document.getElementById('compression-progress-info-' + folderId);
            infoDiv.style.display = 'block';
            infoDiv.textContent = '';
            var startTime = Date.now();
            fetch('download_folder.php?action=compress&folder_id=' + folderId)
                .then(() => pollProgressSingleBtn(folderId, btn, infoDiv, startTime));
        }
        function pollProgressSingleBtn(folderId, btn, infoDiv, startTime) {
            fetch('download_folder.php?action=progress&folder_id=' + folderId)
                .then(r => r.json())
                .then(data => {
                    if (data.status === 'compressing') {
                        var percent = data.percent || 0;
                        var elapsed = (Date.now() - startTime) / 1000;
                        var estTotal = percent > 0 ? elapsed / (percent/100) : 0;
                        var estRemain = estTotal - elapsed;
                        var estText = (percent > 0 && estRemain > 0) ? ` | ~${Math.round(estRemain)}s left` : '';
                        infoDiv.innerHTML = `${percent}%${estText}`;
                        setTimeout(() => pollProgressSingleBtn(folderId, btn, infoDiv, startTime), 500);
                    } else if (data.status === 'done') {
                        btn.disabled = false;
                        btn.classList.remove('compressing');
                        btn.classList.add('download-ready');
                        btn.style.background = '#28a745';
                        btn.innerHTML = '<i class="fas fa-download"></i> <span class="btn-text">Download</span>';
                        infoDiv.innerHTML = '100%';
                    } else if (data.status === 'error') {
                        btn.disabled = false;
                        btn.classList.remove('compressing');
                        btn.style.background = '#dc3545';
                        btn.innerHTML = '<i class="fas fa-exclamation-triangle"></i> <span class="btn-text">Error</span>';
                        infoDiv.innerHTML = 'Error: ' + (data.error || 'Unknown error');
                    } else {
                        btn.disabled = false;
                        btn.classList.remove('compressing');
                        btn.style.background = '#007bff';
                        btn.innerHTML = '<i class="fas fa-file-archive"></i> <span class="btn-text">Compress</span>';
                        infoDiv.style.display = 'none';
                    }
                });
        }

        function compressImagesInFolder(folderId, btn) {
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner"></span> Compressing...';
            var progressDiv = document.getElementById('compress-img-progress-' + folderId);
            progressDiv.style.display = 'block';
            progressDiv.textContent = 'Compressing images...';
            fetch('compress_images.php?folder_id=' + folderId)
                .then(r => r.json())
                .then(data => {
                    btn.disabled = false;
                    btn.innerHTML = '<i class="fas fa-compress"></i> Compress Img';
                    if (data.success) {
                        progressDiv.innerHTML = 'Compressed: ' + data.compressed + ' / ' + data.total + ' images.';
                    } else {
                        progressDiv.innerHTML = 'Error: ' + (data.error || 'Unknown error');
                    }
                })
                .catch(() => {
                    btn.disabled = false;
                    btn.innerHTML = '<i class="fas fa-compress"></i> Compress Img';
                    progressDiv.innerHTML = 'Error during compression.';
                });
        }
    </script>
</body>
</html>