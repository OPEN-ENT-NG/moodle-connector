import {_, model, moment, ng, template} from "entcore";
import {Course, Courses} from "../model";
import {Folder, Folders} from "../model/Folder";
import {Utils} from "../utils/Utils";

export const mainController = ng.controller('MoodleController', ['$scope', '$timeout', 'route', '$rootScope', ($scope, $timeout, route, $rootScope) => {


    route({
        dashboard: function (params) {
            $scope.initController();
            $scope.initDashBoardTab();
        },
        courses: function (params) {
            $scope.initController();
            $scope.initCoursesTab();
        },
        library: function (params) {
            $scope.initController();
            $scope.initLibraryTab();
        }

    });

    $scope.switchTab = function (current: string) {
        $scope.currentTab = current;
        $scope.resetSelect();
        $scope.toasterShow = false;
        if ($scope.currentTab == 'courses') {
            $scope.initCoursesTab();
        } else if ($scope.currentTab == 'dashboard') {
            $scope.initDashBoardTab();
        } else if ($scope.currentTab == 'library') {
            $scope.initLibraryTab();
        } else {
            $scope.initDashBoardTab();
        }
    };

    $scope.initDashBoardTab = async function () {
        $scope.currentTab = 'dashboard';
        // TODO recupérer de la bdd, selon le choix de l'utilisateur connecté


        if ($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
            await $scope.courses.getCoursesbyUser(model.me.userId);
        }

        await $scope.courses.getChoice();

        template.open('main', 'dashboard/dashboard_home');
        Utils.safeApply($scope);
    };

    $scope.initCoursesTab = async function () {
        $scope.currentTab = 'courses';
        // TODO ne charger que si besoin
        if ($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
            await $scope.courses.getCoursesbyUser(model.me.userId);
        }

        //Behaviours.findRights('moodle', $scope.courses.allCourses[0]);
        //$scope.courses.allCourses[0].myRights.fromBehaviours("moodle");

        // TODO gestion des dossiers
        // if($scope.folders.isSynchronized === undefined || $scope.folders.isSynchronized === false) {
        //     $scope.initFolders();
        // }

        template.open('main', 'my-courses');
        Utils.safeApply($scope);
    };

    $scope.initLibraryTab = async function () {
        $scope.currentTab = 'library';
        template.open('main', 'page-library');
        Utils.safeApply($scope);

    };

    $scope.initController = async function () {
        $scope.courses = new Courses();
        $scope.currentTab = 'dashboard';
        $scope.lightboxes = {};
        $scope.params = {};
        $scope.printmenufolder = true;
        $scope.printmenucourseShared = false;
        $scope.currentfolderid = 0;
        $scope.printcours = true;
        $scope.printfolders = true;
        $scope.folders = new Folders();
        $scope.toasterShow = false;
        $scope.openLightbox = false;
        $scope.searchbar = {};
        $scope.openLightboxFolder = false;
        $scope.lightboxFolderMove = false;
        $scope.successDelete = false;
        $scope.typeFilter = [true, true];
        $scope.nbFoldersSelect = 0;
        $scope.nbCoursesSelect = 0;
        $scope.disableDeleteSend = true;
        $scope.typeShow = {
            availableOptions: [
                {id: 'all', name: 'Tout'},
                {id: 'doing', name: 'En cours'},
                {id: 'favorites', name: 'Favoris'},
                {id: 'finished', name: 'Terminés'},
                {id: 'masked', name: 'Masqués'}
            ],
            selectedToDoOption: {id: 'doing', name: 'En cours'},
            selectedToComeOption: {id: 'all', name: 'Tout'}
        };
        $scope.firstCoursesToDo = 0;
        $scope.lastCoursesToDo = 8;
        $scope.firstCoursesToCome = 0;
        $scope.lastCoursesToCome = $scope.countToCome();
        $scope.showInfoSharePanel = false;
        $scope.viewModeToDo = "icons";
        $scope.viewModeToCome = "icons";
        $scope.initFolders();
        $scope.imgCompatibleMoodle = false;
        $scope.typeActivity = {
            availableOptions: [
                {id: 'quiz', name: 'Quizz'},
                {id: 'file', name: 'Fichier'},
                {id: 'page', name: 'Page'},
                {id: 'assignment', name: 'Devoir'}
            ],
            selectedOption: {id: undefined, name: 'Choisissez votre type'}
        };
        $scope.course.submitWait = false;
    };

    $scope.isPrintMenuFolder = function () {
        $scope.printmenufolder = !$scope.printmenufolder;
        $scope.printmenucourseShared = false;
        if ($scope.printmenufolder) {
            $scope.printfolders = true;
            $scope.printcours = true;
            $scope.currentfolderid = 0;
            $scope.printCouresbySubFolder($scope.currentfolderid);
        } else {
            $scope.printfolders = false;
            $scope.printcours = false;
            $scope.currentfolderid = 0;
            $scope.setprintsubfolderValue();
        }
    };
    $scope.isPrintMenuCoursesShared = function () {
        $scope.printmenucourseShared = !$scope.printmenucourseShared;
        $scope.printmenufolder = false;
        $scope.printfolders = false;
        $scope.currentfolderid = 0;
        if ($scope.printmenucourseShared) {
            $scope.printcours = true;
            $scope.initAllCouresbyuser();
        } else {
            $scope.printcours = false;
        }
        $scope.setprintsubfolderValue();
    };
    $scope.setprintsubfolderValue = function () {
        $scope.folders.all.forEach(function (e) {
            e.printsubfolder = false;
        });
        Utils.safeApply($scope);
    };
    $scope.setprintsubfolderValuebyFolder = function (folder: Folder, printsubfolder: boolean) {
        $scope.folders.all.forEach(function (e) {
            if (e.id != folder.parent_id && e.id != folder.id && e.id != 0)
                e.printsubfolder = false;
        });
        folder.printsubfolder = printsubfolder;
        $scope.parent = folder.parent_id;
        while ($scope.parent != 0) {
            $scope.folders.all.forEach(function (e) {
                if (e.id == $scope.parent) {
                    e.printsubfolder = true;
                    $scope.parent = e.parent_id;
                }
            });
        }
        Utils.safeApply($scope);
    };
    $scope.isPrintSubFolder = function (folder: Folder) {
        $scope.folders.all.forEach(function (e) {
            if (e.id == folder.id) {
                e.printsubfolder = !e.printsubfolder;
                folder.printsubfolder = e.printsubfolder;
            }
        });

        if (folder.printsubfolder) {
            $scope.currentfolderid = folder.id;
            $scope.printcours = true;
            $scope.printCouresbySubFolder(folder.id);
        } else {
            (folder.parent_id != folder.id) ? $scope.currentfolderid = folder.parent_id : $scope.currentfolderid = 0;
            $scope.printCouresbySubFolder($scope.currentfolderid);
        }
        $scope.setprintsubfolderValuebyFolder(folder, folder.printsubfolder);
    };

    $scope.printCouresbySubFolder = function (idfolder: number) {
        $scope.initCouresbyFolder(idfolder);
        $scope.printcours = true;
        $scope.printfolders = true;

    };

    $scope.initCoursesbyuser = async function () {
        await $scope.courses.getCoursesbyUser();
        Utils.safeApply($scope);
    };
    $scope.initCouresbyFolder = async function (idfolder: number) {
        //await $scope.courses.getCoursesbyFolder(idfolder);
        Utils.safeApply($scope);
    };

    /*$scope.initAllCouresbyuser = async function(){
        await $scope.courses.getCoursesAndSheredbyFolder();
        Utils.safeApply($scope);
    };*/

    $scope.initFolders = async function () {
        $scope.folders = new Folders();
        await $scope.folders.sync();
        $scope.resetSelect();
        $scope.countItems();
        $scope.showToaster();
        Utils.safeApply($scope);
    };

    $scope.getFolderParent = function (): Folder[] {
        return $scope.folders.getparentFolder();
    };
    $scope.getSubFolder = function (folder: Folder): Folder[] {
        return $scope.folders.getSubFolder(folder.id);
    };

    $scope.countItems = async function (folder: Folder) {
        if (folder) {
            await folder.countItemsModel();
        }
    };

    /**
     * Open creation course lightbox
     */
    $scope.openPopUp = function () {
        $scope.course = new Course();
        template.open('ligthBoxContainer', 'courses/createCourseLightbox');
        $scope.typeActivity.selectedOption.id = undefined;
        $scope.openLightbox = true;
    };
    /**
     * Close lightbox
     */
    $scope.closePopUp = function () {
        $scope.openLightbox = false;
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = false);
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = false);
    };

    /**
     * Create a course
     */
    $scope.createCourse = async function () {
        // TODO get current folder id
        $scope.course.folderid = $scope.currentfolderid;
        $scope.course.submitWait = true;
        await $scope.course.create();
        await $scope.courses.getCoursesbyUser(model.me.userId);
        $scope.openLightbox = false;
        $scope.course.submitWait = false;
        Utils.safeApply($scope);
    };

    $scope.changeTypeA = function(course : Course) {
        course.typeA = $scope.typeActivity.selectedOption.id;
    }

    // $scope.shareCourse = async function () {
    //     $scope.course = new Course();
    //     $scope.course.share();
    //     Utils.safeApply($scope);
    // };
    /*
        $scope.deleteCourse = function () {
            $scope.course.delete();
            Utils.safeApply($scope);
        };
    */
    /**
     * Filter Type
     */
    $scope.checkTypeFilterSelected = function (id: number) {
        $scope.typeFilter[id] = !$scope.typeFilter[id];
    };

    $scope.showCourse = function (courseType: string) {
        if (courseType == "topics")
            return $scope.typeFilter[0];
        else
            return $scope.typeFilter[1];
    };

    $scope.getAllFolders = function () {
        return $scope.folders.getAllFoldersModel();
    };
    $scope.resetSelect = function () {
        $scope.folders.all.map(folder => folder.select = false);
        $scope.courses.allCourses.map(course => course.select = false);
    };
    /**
     * toaster show
     * */
    $scope.showToaster = function () {
        template.open('toaster', 'toaster');
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        $scope.countFoldersCourses();
        if ($scope.toasterShow === true) {
            $scope.selectedCourse = _.findWhere($scope.courses.coursesByUser, {select: true});
        }
    };
    /**
     * count folders and courses select
     * */
    $scope.countFoldersCourses = function () {
        $scope.nbFoldersSelect = $scope.folders.all.filter(folder => folder.select).length;
        $scope.nbCoursesSelect = $scope.courses.allCourses.filter(course => course.select).length;

    };
    /**
     * create folder
     * */
    $scope.openPopUpFolder = function () {
        $scope.folder = new Folder();
        template.open('ligthBoxContainer', 'courses/createFolderLightbox');
        $scope.openLightbox = true;
    };

    $scope.createFolder = async function () {
        $scope.folder.parent_id = $scope.currentfolderid;
        await $scope.folder.create();
        $scope.initFolders();
        $scope.openLightbox = false;
    };
    /**
     * delete elements
     * */

    $scope.openPopUpDelete = function () {
        template.open('ligthBoxContainer', 'courses/deleteLightbox');
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = true);
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = true);
        $scope.confirmDeleteSend();
        $scope.openLightbox = true;
    };
    $scope.hideSuccessDelete = function () {
        $scope.successDelete = false;
    };
    $scope.deleteElements = async function () {
        $scope.disableDeleteSend = false;
        if ($scope.folders.all.some(folder => folder.selectConfirm)) {
            await $scope.folders.foldersDelete();
        }
        if ($scope.courses.allCourses.some(course => course.selectConfirm)) {
            await $scope.courses.coursesDelete();
            await $scope.courses.getCoursesbyUser(model.me.userId);
        }
        $scope.openLightbox = false;
        $scope.successDelete = true;
        $timeout(() =>
                $scope.successDelete = false
            , 3000);
        $scope.initFolders();
    };
    /**
     * move folders
     * */
    $scope.openPopUpFolderMove = function () {
        template.open('ligthBoxContainer', 'courses/moveElementLightbox');
        $scope.openLightbox = true;
    };
    $scope.foldersMove = async function () {
        await $scope.folders.moveFolders();
        $scope.initFolders();
        $scope.openLightbox = false;
    };
    /**
     * confirm delete
     * */
    $scope.confirmDeleteSend = function () {
        $scope.disableDeleteSend = !!($scope.folders.all.some(folder => folder.selectConfirm) || $scope.courses.allCourses.some(course => course.selectConfirm));
    };

    /**
     * next and previous button to show courses
     * */

    $scope.previousCoursesToDoButton = function () {
        $scope.firstCoursesToDo -= $scope.countPreviousToDo();

        if ($scope.firstCoursesToDo < 0) {
            $scope.firstCoursesToDo = 0;
            //$scope.countForwardToDo();
        }
    };
    $scope.nextCoursesToDoButton = function () {
        $scope.firstCoursesToDo = $scope.courses.coursesToDoWithImage;
        //$scope.countForwardToDo();
    };

    $scope.countToCome = function () {
        if ($scope.viewModeToCome == 'list') {
            return 5;
        } else {
            return 4;
        }
    };

    /*$scope.countForwardToDo = function () {
        if ($scope.viewModeToDo == 'list') {
            $scope.courses.coursesToDoWithImage = 5;
        }else{
            $scope.lastCoursesToDo = 9;
            for (let i = 0; i < $scope.lastCoursesToDo; i++) {
                if ($scope.courses.coursesToShow($scope.typeShow.selectedToDoOption.id,'coursesToDo',$scope.firstCoursesToDo,$scope.viewModeToDo).slice(firstNbr, firstNbr + 8)[i].imageurl !== null && courses.slice(firstNbr, firstNbr + 8)[i].imageurl !== undefined && courses.slice(firstNbr, firstNbr + 8)[i].imageurl !== '-') {
                    $scope.lastCoursesToDo--;
                }
            }
            this.coursesToDoWithImage = $scope.lastCoursesToDo;
        }
    };*/

    $scope.countPreviousToDo = function () {
        if ($scope.viewModeToDo == 'list') {
            return 5;
        }else{
            $scope.lastCoursesToDo = 8;
            let i = 1
            while (i <= $scope.lastCoursesToDo && $scope.firstCoursesToDo-i >= 0) {
                if ($scope.courses.coursesToShow($scope.typeShow.selectedToDoOption.id,'coursesToDo',$scope.firstCoursesToDo,$scope.viewModeToDo)[$scope.firstCoursesToDo-i].imageurl !== null &&
                    $scope.courses.coursesToShow($scope.typeShow.selectedToDoOption.id,'coursesToDo',$scope.firstCoursesToDo,$scope.viewModeToDo)[$scope.firstCoursesToDo-i].imageurl !== undefined &&
                    $scope.courses.coursesToShow($scope.typeShow.selectedToDoOption.id,'coursesToDo',$scope.firstCoursesToDo,$scope.viewModeToDo)[$scope.firstCoursesToDo-i].imageurl !== '-') {
                    if(i == 5)
                        $scope.lastCoursesToDo--;
                    $scope.lastCoursesToDo--;
                }
                i++;
            }
            return $scope.lastCoursesToDo;
        }
    };

    $scope.previousCoursesToComeButton = function () {
        $scope.firstCoursesToCome -= $scope.countToCome();
        $scope.lastCoursesToCome -= $scope.countToCome();
        if($scope.firstCoursesToCome < 0){
            $scope.firstCoursesToCome = 0;
            $scope.lastCoursesToCome = $scope.countToCome();
        }
    };

    $scope.nextCoursesToComeButton = function () {
        $scope.firstCoursesToCome = $scope.lastCoursesToCome ;
        $scope.lastCoursesToCome += $scope.countToCome();
    };

    /**
     * print the right format of course data
     * */

    $scope.printRightFormatDate = function (course: Course, spec: string) {
        let format = "DD/MM/YYYY";
        if (spec == "modified") {
            return moment(course.date + "000", "x").format(format);
        } else if (spec == "enddate") {
            return moment(course.enddate + "000", "x").format(format);
        } else if (spec == "begindate") {
            return moment(course.startdate + "000", "x").format(format);
        }
        return moment();
    };

    $scope.printRightFormatAuthor = function (course: Course) {
        let author = course.auteur[0].firstname[0] + ". " + course.auteur[0].lastname[0].toUpperCase() + course.auteur[0].lastname.slice(1).toLowerCase();
        return author;
    };

    /**
     * change and get the view mode of courses to do and to come
     * */

    $scope.changeViewModeToCome = function (view : string) {
        $scope.viewModeToCome = view;
        $scope.lastCoursesToCome = $scope.firstCoursesToCome + $scope.countToCome();
    };

    $scope.changeViewModeToDo = function (view : string) {
        $scope.viewModeToDo = view;
    };

    $scope.changeShowCoursesToDo = function() {
        $scope.firstCoursesToDo=0;
    };

    $scope.changeShowCoursesToCome = function() {
        $scope.firstCoursesToCome=0;
    };

    $scope.getSelectedCourses = function () {
        return _.where($scope.courses.coursesByUser, {select: true});
    };
    $scope.getTheSelectedCourse = function () {
        $scope.myCourse = _.findWhere($scope.courses.coursesByUser, {select: true});
    };

    // TODO remplacer par balise authorize dans toaster.html
    // <authorize name="manage" resource="uploads.selection()"> ...
    $scope.showShareButton = function () {
        return $scope.getSelectedCourses().length === 1;
    };

    /**
     * get info image
     * */
    $scope.getTypeImage = function () {
        if ($scope.course.imageurl) {
            $scope.course.setInfoImg();
            $timeout(() =>
                    $scope.imgCompatibleMoodle = $scope.course.infoImg.compatibleMoodle
                , 1000)
        }
    };

    $scope.showInfoShare = () => {
        $scope.getTheSelectedCourse();
        $scope.showInfoSharePanel = true;
    };
    ;
    /**
     * Close lightbox Share
     */
    $scope.closePopUpShare = () => {
        $scope.showInfoSharePanel = false;
    };
    $scope.submitShareCourse = () => {
        $scope.initFolders();
    }
}]);
