;;; lg.el --- A front-end for lg ('lightgrok'), the source reading helper.

;; Copyright (C) 2017 Joyer Huang <collger@gmail.com>
;;
;; Author: Joyer Huang <collger@gmail.com>
;; Created: 8 July 2017
;; Version: 0.1

;;; Commentary:

;; This file is heavily based on the excellent ag.el.

;; Usage:

;; Add you to your .emacs.d:

;; (add-to-list 'load-path "/path/to/lg.el") ;; optional
;; (require 'lg)

;; Alternatively, just install the package from MELPA.

;; If you're using lg 0.14+, which supports --color-match, then you
;; can add highlighting with:

;; (setq lg-highlight-search t)

;; I like to bind the *-at-point commands to F5 and F6:

;; (global-set-key (kbd "<f5>") 'lg-project-at-point)
;; (global-set-key (kbd "<f6>") 'lg-regexp-project-at-point)

;;; License:

;; This file is not part of GNU Emacs.
;; However, it is distributed under the same license.

;; GNU Emacs is free software; you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3, or (at your option)
;; any later version.

;; GNU Emacs is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.

;; You should have received a copy of the GNU General Public License
;; along with GNU Emacs; see the file COPYING.  If not, write to the
;; Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
;; Boston, MA 02110-1301, USA.

;;; Code:

(defcustom lg-highlight-search nil
  "Non-nil means we highlight the current search term in results.

This requires the lg command to support --color-match, which is only in v0.14+"
  :type 'boolean
  :group 'lg)

(require 'compile)

(defvar lg-match-face 'match
  "Face name to use for lg matches.")


(define-compilation-mode lg-mode "Lg"
  "Lg results compilation mode"
  (let ((smbl  'compilation-lg-nogroup)
        (filen '("^\\([^: \n]+?\\)" 1 nil))
        (pttrn '("^\\([^:\n]+?\\):\\([0-9]+\\):" 1 2)))
    (set (make-local-variable 'compilation-error-regexp-alist) (list smbl))
    (set (make-local-variable 'compilation-error-regexp-alist-alist) (list (cons smbl filen)))
    (set (make-local-variable 'compilation-error-regexp-alist-alist) (list (cons smbl pttrn))))
  (add-hook 'compilation-filter-hook 'lg-filter nil t))

(defun lg/s-join (separator strings)
  "Join all the strings in STRINGS with SEPARATOR in between."
  (mapconcat 'identity strings separator))

(defun lg/s-replace (old new s)
  "Replace all occurrences of OLD in NEW in S."
  (replace-regexp-in-string (regexp-quote old) new s t t))

(defun lg/shell-quote (string)
  "Wrap STRING in single quotes, and quote existing single quotes to make shell safe."
  (concat "'" (lg/s-replace "'" "'\\''" string) "'"))

(defun lg/search (string directory &optional regexp)
  "Run lg searching for the STRING given in DIRECTORY.
If REGEXP is non-nil, treat STRING as a regular expression."
  (letrec ((default-directory (file-name-as-directory directory))
           (arguments (list "-root" default-directory))
		   (compilation-scroll-output t))
    (unless (file-exists-p default-directory)
      (error "No such directory %s" default-directory))
    (compilation-start
     (lg/s-join " "
                (append '("lightgrok") arguments (list "-search" (lg/shell-quote string))))
     'lg-mode
     (function (lambda (ignore) (concat "*lg-" string "*"))))))

(defun lg/search/file (string directory &optional regexp)
  "Run lg searching for the STRING given in DIRECTORY for filename.
If REGEXP is non-nil, treat STRING as a regular expression."
  (let ((default-directory (file-name-as-directory directory))
        (arguments (if regexp
                       (list "--nogroup" "-g" )
                     (list "--literal" "--nogroup" "-g"))))
    (if lg-highlight-search
        (setq arguments (append '("--color" "--color-match" "'30;43'") arguments))
      (setq arguments (append '("--nocolor") arguments)))
    (unless (file-exists-p default-directory)
      (error "No such directory %s" default-directory))
    (compilation-start
     (lg/s-join " "
                (append '("lg") arguments (list (lg/shell-quote string))))
     'lg-mode)))

(defun lg/dwim-at-point ()
  "If there's an active selection, return that.
Otherwise, get the symbol at point."
  (if (use-region-p)
      (buffer-substring-no-properties (region-beginning) (region-end))
    (if (symbol-at-point)
        (symbol-name (symbol-at-point)))))

(autoload 'vc-git-root "vc-git")
(autoload 'vc-svn-root "vc-svn")

(defun lg/project-root (file-path)
  "Guess the project root of the given FILE-PATH."
  (or (vc-git-root file-path)
      (vc-svn-root file-path)
      file-path))

;;;###autoload
(defun lg (string directory)
  "Search using lg in a given DIRECTORY for a given search STRING."
  (interactive "sSearch string: \nDDirectory: ")
  (lg/search string directory))

;;;###autoload
(defun lg-regexp (string directory)
  "Search using lg in a given directory for a given regexp."
  (interactive "sSearch regexp: \nDDirectory: ")
  (lg/search string directory t))

;;;###autoload
(defun lg-project (string)
  "Guess the root of the current project and search it with lg
for the given string."
  (interactive "sSearch string: ")
  (lg/search string (lg/project-root default-directory)))

;;;###autoload
(defun lg-project-regexp (regexp)
  "Guess the root of the current project and search it with lg
for the given regexp."
  (interactive "sSearch regexp: ")
  (lg/search regexp (lg/project-root default-directory) t))

;;;###autoload
(defun lg-project-filename (regexp)
  "Guess the root of the current project and search it with lg -g
for the given regexp."
  (interactive "sSearch regexp: ")
  (lg/search/file regexp (lg/project-root default-directory) t))

(autoload 'symbol-at-point "thingatpt")

;;;###autoload
(defun lg-project-at-point (string)
  "Same as ``lg-project'', but with the search string defaulting
to the symbol under point."
   (interactive (list (read-from-minibuffer "Search string: " (lg/dwim-at-point))))
   (lg/search string (lg/project-root default-directory)))

;;;###autoload
(defun lg-regexp-project-at-point (regexp)
  "Same as ``lg-regexp-project'', but with the search regexp defaulting
to the symbol under point."
   (interactive (list (read-from-minibuffer "Search regexp: " (lg/dwim-at-point))))
   (lg/search regexp (lg/project-root default-directory) t))

;; Taken from grep-filter, just changed the color regex.
(defun lg-filter ()
  "Handle match highlighting escape sequences inserted by the lg process.
This function is called from `compilation-filter-hook'."
  (when lg-highlight-search
    (save-excursion
      (forward-line 0)
      (let ((end (point)) beg)
        (goto-char compilation-filter-start)
        (forward-line 0)
        (setq beg (point))
        ;; Only operate on whole lines so we don't get caught with part of an
        ;; escape sequence in one chunk and the rest in another.
        (when (< (point) end)
          (setq end (copy-marker end))
          ;; Highlight lg matches and delete marking sequences.
          (while (re-search-forward "\033\\[30;43m\\(.*?\\)\033\\[[0-9]*m" end 1)
            (replace-match (propertize (match-string 1)
                                       'face nil 'font-lock-face lg-match-face)
                           t t))
          ;; Delete all remaining escape sequences
          (goto-char beg)
          (while (re-search-forward "\033\\[[0-9;]*[mK]" end 1)
            (replace-match "" t t)))))))

(provide 'lg)
;;; lg.el ends here
